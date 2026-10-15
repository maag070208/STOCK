import { type Column, ITBadget, ITButton, ITConfirmDialog, ITDropdownMenu, ITFlex, ITPage, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaEllipsisV } from "react-icons/fa";
import { useNavigate, useParams } from "react-router-dom";
import { partnerApi, type Contact, type Partner, type PartnerKind } from "@entities/partner";
import { useCan } from "@entities/user";
import { ContactDialog } from "@features/partner/ContactDialog";
import { ApiError } from "@shared/api/client";
import { formatDateTime } from "@shared/lib/format";
import { toast } from "@app/store";
import { FactStrip } from "@shared/ui/FactStrip";
import { PlainTable } from "@shared/ui/PlainTable";

type Confirm = { kind: "deactivateContact" | "reactivateContact"; contact: Contact };

// One row of the contacts: the contact, which the kit tells apart by its id.
type ContactRow = Record<string, unknown> & { id: string; contact: Contact };

// One supplier or customer: its data, and its contacts with their actions for who can manage. The way back is the
// breadcrumbs, and every piece of the screen is a component of the kit (web.md section 8).
export function PartnerPage({ kind }: { kind: PartnerKind }) {
	const { t, i18n } = useTranslation(["partners", "common"]);
	const { id = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useCan(`${kind}.manage`);
	const api = partnerApi(kind);
	const [partner, setPartner] = useState<Partner | null>(null);
	const [missing, setMissing] = useState(false);
	const [editing, setEditing] = useState<{ contact: Contact | null } | null>(null);
	const [confirm, setConfirm] = useState<Confirm | null>(null);

	const load = useCallback(async () => {
		try {
			setPartner(await partnerApi(kind).get(id));
		} catch {
			setMissing(true);
		}
	}, [kind, id]);

	useEffect(() => {
		void load();
	}, [load]);

	// The list this screen hangs from, and the record itself: the breadcrumbs are the way back.
	const breadcrumbs = [{ label: t(`${kind}.title`), onClick: () => navigate(`/${kind}`) }, { label: partner ? `${partner.code} · ${partner.name}` : t(`${kind}.title`) }];

	if (missing) {
		return (
			<ITPage title={t(`${kind}.title`)} breadcrumbs={breadcrumbs} noPadding>
				<ITText>{t("common:errors.notFound")}</ITText>
			</ITPage>
		);
	}

	async function confirmed() {
		if (!confirm) return;
		const { kind: action, contact } = confirm;
		setConfirm(null);
		try {
			await (action === "deactivateContact" ? api.deactivateContact(id, contact.id) : api.reactivateContact(id, contact.id));
			toast(t("common:saved"));
			await load();
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
		}
	}

	const address = partner?.address;
	const addressText = address
		? `${address.street} ${address.exteriorNumber}${address.interiorNumber ? `, ${t("detail.interior", { number: address.interiorNumber })}` : ""}\n` +
			`${address.neighborhood}, ${address.postalCode} ${address.city}\n${address.state?.label ?? ""}, ${address.country?.label ?? ""}`
		: "";
	const contacts: ContactRow[] = (partner?.contacts ?? []).map((contact) => ({ id: contact.id, contact }));

	const columns: Column<ContactRow>[] = [
		{ key: "name", label: t("contacts.name"), type: "string", render: (row) => row.contact.name },
		{ key: "email", label: t("contacts.email"), type: "string", render: (row) => row.contact.email ?? "" },
		{ key: "phone", label: t("contacts.phone"), type: "string", render: (row) => row.contact.phone ?? "" },
		{
			key: "status",
			label: t("columns.status"),
			type: "string",
			render: (row) => <ITBadget color={row.contact.active ? "success" : "gray"} label={t(row.contact.active ? "common:status.active" : "common:status.inactive")} />,
		},
		{
			key: "actions",
			label: "",
			type: "actions",
			className: "sf:text-right",
			actions: (row) =>
				canManage ? (
					<ITDropdownMenu
						trigger={<FaEllipsisV aria-hidden />}
						triggerLabel={t("columns.actions")}
						placement="bottom-end"
						items={[
							{ id: "edit", label: t("actions.edit"), onClick: () => setEditing({ contact: row.contact }) },
							row.contact.active
								? { id: "deactivate", label: t("contacts.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivateContact", contact: row.contact }) }
								: { id: "reactivate", label: t("contacts.reactivate"), onClick: () => setConfirm({ kind: "reactivateContact", contact: row.contact }) },
						]}
					/>
				) : null,
		},
	];

	return (
		<ITPage
			title={partner?.code ?? t(`${kind}.title`)}
			description={partner?.name ?? undefined}
			breadcrumbs={breadcrumbs}
			noPadding
			loading={!partner}
			actions={canManage ? <ITButton label={t("actions.edit")} onClick={() => navigate(`/${kind}/${id}/edit`)} /> : undefined}
		>
			<ITStack spacing={8}>
				{partner && address && (
					<ITStack spacing={4}>
						<FactStrip
							perRow={2}
							facts={[
								{ label: t("columns.taxId"), value: partner.taxId },
								{ label: t("detail.paymentTerms"), value: partner.paymentTerms?.label ?? partner.paymentTerms?.code ?? "" },
								{ label: t("detail.createdAt"), value: formatDateTime(partner.createdAt, i18n.language) },
								{ label: t("detail.address"), value: addressText },
							]}
						/>
						<ITFlex align="center" gap={2}>
							<ITText muted className="sf:text-label-md">
								{t("columns.status")}
							</ITText>
							<ITBadget color={partner.active ? "success" : "gray"} label={t(partner.active ? "common:status.active" : "common:status.inactive")} />
						</ITFlex>
					</ITStack>
				)}

				<ITStack spacing={3} className="sf:max-w-3xl">
					<ITFlex align="center" justify="between">
						<ITText as="h2" className="sf:text-title-md">
							{t("contacts.title")}
						</ITText>
						{canManage && partner && <ITButton size="sm" label={t("contacts.new")} onClick={() => setEditing({ contact: null })} />}
					</ITFlex>
					{partner?.contacts?.length === 0 && <ITText muted>{t("contacts.none")}</ITText>}
					{!!partner?.contacts?.length && (
						<PlainTable<ContactRow>
							columns={columns}
							data={contacts}
							size="sm"
						/>
					)}
				</ITStack>
			</ITStack>

			<ContactDialog
				kind={kind}
				partnerId={id}
				contact={editing?.contact ?? null}
				isOpen={!!editing}
				onClose={() => setEditing(null)}
				onSaved={() => {
					setEditing(null);
					toast(t("common:saved"));
					void load();
				}}
			/>
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(confirm.kind === "deactivateContact" ? "contacts.deactivate" : "contacts.reactivate") : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { name: confirm.contact.name }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivateContact" ? "danger" : "primary"}
			/>
		</ITPage>
	);
}
