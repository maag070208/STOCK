import { ITBadget, ITButton, ITConfirmDialog, ITDropdownMenu, ITPage } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { partnerApi, type Contact, type Partner, type PartnerKind } from "@entities/partner";
import { useCan } from "@entities/user";
import { ContactDialog } from "@features/partner/ContactDialog";
import { ApiError } from "@shared/api/client";
import { formatDateTime } from "@shared/lib/format";
import { toast } from "@app/store";

type Confirm = { kind: "deactivateContact" | "reactivateContact"; contact: Contact };

// One supplier or customer: its data, and its contacts with their actions for who can manage.
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

	const back = <ITButton variant="outlined" label={t(`${kind}.back`)} onClick={() => navigate(`/${kind}`)} />;
	if (missing) {
		return (
			<ITPage title={t(`${kind}.title`)} noPadding>
				<p className="sf:text-body-md">{t("common:errors.notFound")}</p>
				{back}
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
	const fields: [string, React.ReactNode][] = partner && address
		? [
				[t("columns.taxId"), partner.taxId],
				[t("detail.paymentTerms"), partner.paymentTerms?.label ?? partner.paymentTerms?.code ?? ""],
				[
					t("detail.address"),
					<span className="sf:whitespace-pre-line">
						{`${address.street} ${address.exteriorNumber}${address.interiorNumber ? `, ${t("detail.interior", { number: address.interiorNumber })}` : ""}\n` +
							`${address.neighborhood}, ${address.postalCode} ${address.city}\n${address.state?.label ?? ""}, ${address.country?.label ?? ""}`}
					</span>,
				],
				[t("detail.createdAt"), formatDateTime(partner.createdAt, i18n.language)],
				[t("columns.status"), <ITBadget color={partner.active ? "success" : "gray"} label={t(partner.active ? "common:status.active" : "common:status.inactive")} />],
			]
		: [];

	return (
		<ITPage
			title={partner ? `${partner.code} · ${partner.name}` : t(`${kind}.title`)}
			noPadding
			loading={!partner}
			actions={
				<div className="sf:flex sf:gap-2">
					{back}
					{canManage && <ITButton label={t("actions.edit")} onClick={() => navigate(`/${kind}/${id}/edit`)} />}
				</div>
			}
		>
			<div className="sf:flex sf:flex-col sf:gap-8">
				<dl className="sf:grid sf:max-w-3xl sf:grid-cols-[max-content_1fr] sf:gap-x-8 sf:gap-y-3" data-testid="partner-detail">
					{fields.map(([label, value]) => (
						<div key={label} className="sf:contents">
							<dt className="sf:text-label-md sf:text-on-surface-muted">{label}</dt>
							<dd className="sf:text-body-md">{value}</dd>
						</div>
					))}
				</dl>

				<section aria-label={t("contacts.title")} className="sf:flex sf:max-w-3xl sf:flex-col sf:gap-3" data-testid="contacts">
					<div className="sf:flex sf:items-center sf:justify-between">
						<h2 className="sf:text-title-md">{t("contacts.title")}</h2>
						{canManage && partner && <ITButton size="sm" label={t("contacts.new")} onClick={() => setEditing({ contact: null })} />}
					</div>
					{partner?.contacts?.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("contacts.none")}</p>}
					{!!partner?.contacts?.length && (
						<table className="sf:w-full sf:text-left sf:text-body-md">
							<thead>
								<tr className="sf:text-label-md sf:text-on-surface-muted">
									<th className="sf:py-2">{t("contacts.name")}</th>
									<th className="sf:py-2">{t("contacts.email")}</th>
									<th className="sf:py-2">{t("contacts.phone")}</th>
									<th className="sf:py-2">{t("columns.status")}</th>
									<th className="sf:py-2" />
								</tr>
							</thead>
							<tbody>
								{partner.contacts.map((contact) => (
									<tr key={contact.id} className="sf:border-t sf:border-outline-variant" data-contact={contact.name}>
										<td className="sf:py-2">{contact.name}</td>
										<td className="sf:py-2">{contact.email ?? ""}</td>
										<td className="sf:py-2">{contact.phone ?? ""}</td>
										<td className="sf:py-2">
											<ITBadget color={contact.active ? "success" : "gray"} label={t(contact.active ? "common:status.active" : "common:status.inactive")} />
										</td>
										<td className="sf:py-2 sf:text-right">
											{canManage && (
												<ITDropdownMenu
													triggerLabel={t("columns.actions")}
													placement="bottom-end"
													items={[
														{ id: "edit", label: t("actions.edit"), onClick: () => setEditing({ contact }) },
														contact.active
															? { id: "deactivate", label: t("contacts.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivateContact", contact }) }
															: { id: "reactivate", label: t("contacts.reactivate"), onClick: () => setConfirm({ kind: "reactivateContact", contact }) },
													]}
												/>
											)}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					)}
				</section>
			</div>

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
