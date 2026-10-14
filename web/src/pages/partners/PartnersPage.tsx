import { ITBadget, ITButton, ITConfirmDialog, ITDataTable, ITDropdownMenu, ITPage, type Column, type ITDropdownMenuItem } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { partnerApi, type Partner, type PartnerKind } from "@entities/partner";
import { useCan } from "@entities/user";
import type { TableParams } from "@shared/api/table";
import { ApiError } from "@shared/api/client";
import { toast } from "@app/store";

type Confirm = { kind: "deactivate" | "reactivate"; partner: Partner };

// The list of suppliers or of customers. Who can manage creates, edits and deactivates them.
export function PartnersPage({ kind }: { kind: PartnerKind }) {
	const { t } = useTranslation(["partners", "common"]);
	const navigate = useNavigate();
	const canManage = useCan(`${kind}.manage`);
	const api = partnerApi(kind);
	const [reload, setReload] = useState(0);
	const [confirm, setConfirm] = useState<Confirm | null>(null);

	const columns: Column<Partner>[] = [
		{ key: "code", label: t("columns.code"), type: "string", filter: "search", sortable: true },
		{ key: "name", label: t("columns.name"), type: "string", filter: "search", sortable: true },
		{ key: "taxId", label: t("columns.taxId"), type: "string", filter: "search", sortable: true },
		{
			key: "active",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: [{ id: "true", name: t("common:status.active") }, { id: "false", name: t("common:status.inactive") }] },
			render: (partner) => <ITBadget color={partner.active ? "success" : "gray"} label={t(partner.active ? "common:status.active" : "common:status.inactive")} />,
		},
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (partner) => {
				const items: ITDropdownMenuItem[] = [{ id: "open", label: t("actions.open"), onClick: () => navigate(`/${kind}/${partner.id}`) }];
				if (canManage) {
					items.push({ id: "edit", label: t("actions.edit"), onClick: () => navigate(`/${kind}/${partner.id}/edit`) });
					if (partner.active) items.push({ id: "deactivate", label: t("actions.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivate", partner }) });
					else items.push({ id: "reactivate", label: t("actions.reactivate"), onClick: () => setConfirm({ kind: "reactivate", partner }) });
				}
				return <ITDropdownMenu items={items} triggerLabel={t("columns.actions")} placement="bottom-end" />;
			},
		},
	];

	async function confirmed() {
		if (!confirm) return;
		const { kind: action, partner } = confirm;
		setConfirm(null);
		try {
			await (action === "deactivate" ? api.deactivate(partner.id) : api.reactivate(partner.id));
			toast(t("common:saved"));
			setReload((value) => value + 1);
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
		}
	}

	return (
		<ITPage title={t(`${kind}.title`)} description={t(`${kind}.description`)} noPadding actions={canManage ? <ITButton label={t(`${kind}.new`)} onClick={() => navigate(`/${kind}/new`)} /> : undefined}>
			<ITDataTable<Partner>
				columns={columns}
				fetchData={(params) => api.query(params as TableParams)}
				reloadTrigger={reload}
				layout="fixed"
				onRowClick={(partner) => navigate(`/${kind}/${partner.id}`)}
			/>
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(`actions.${confirm.kind}`) : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { name: confirm.partner.name }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivate" ? "danger" : "primary"}
			/>
		</ITPage>
	);
}
