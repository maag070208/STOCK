import { type Column, ITBadget, ITButton, ITConfirmDialog, ITDataTable, ITDropdownMenu, type ITDropdownMenuItem, ITPage } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FaEllipsisV } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { useCan } from "@entities/user";
import { warehouseApi, type Warehouse } from "@entities/warehouse";
import { WarehouseDialog } from "@features/warehouse/WarehouseDialog";
import type { TableParams } from "@shared/api/table";
import { ApiError } from "@shared/api/client";
import { toast } from "@app/store";

type Confirm = { kind: "deactivate" | "reactivate"; warehouse: Warehouse };

// The warehouses the person can see: every one with ALL, their own with AREA (the API decides).
export function WarehousesPage() {
	const { t } = useTranslation(["warehouses", "common"]);
	const navigate = useNavigate();
	const canManage = useCan("locations.manage");
	const [reload, setReload] = useState(0);
	const [editing, setEditing] = useState<{ warehouse: Warehouse | null } | null>(null);
	const [confirm, setConfirm] = useState<Confirm | null>(null);
	const refresh = () => setReload((value) => value + 1);

	const columns: Column<Warehouse>[] = [
		{ key: "code", label: t("columns.code"), type: "string", filter: "search", sortable: true },
		{ key: "name", label: t("columns.name"), type: "string", filter: "search", sortable: true },
		{
			key: "active",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: [{ id: "true", name: t("common:status.active") }, { id: "false", name: t("common:status.inactive") }] },
			render: (warehouse) => <ITBadget color={warehouse.active ? "success" : "gray"} label={t(warehouse.active ? "common:status.active" : "common:status.inactive")} />,
		},
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (warehouse) => {
				const items: ITDropdownMenuItem[] = [{ id: "open", label: t("actions.open"), onClick: () => navigate(`/warehouses/${warehouse.id}`) }];
				if (canManage) {
					items.push({ id: "edit", label: t("actions.edit"), onClick: () => setEditing({ warehouse }) });
					if (warehouse.active) items.push({ id: "deactivate", label: t("actions.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivate", warehouse }) });
					else items.push({ id: "reactivate", label: t("actions.reactivate"), onClick: () => setConfirm({ kind: "reactivate", warehouse }) });
				}
				return <ITDropdownMenu items={items} trigger={<FaEllipsisV aria-hidden />} triggerLabel={t("columns.actions")} placement="bottom-end" />;
			},
		},
	];

	async function confirmed() {
		if (!confirm) return;
		const { kind, warehouse } = confirm;
		setConfirm(null);
		try {
			await (kind === "deactivate" ? warehouseApi.deactivate(warehouse.id) : warehouseApi.reactivate(warehouse.id));
			toast(t("common:saved"));
			refresh();
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
		}
	}

	return (
		<ITPage breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title") })} title={t("title")} description={t("description")} noPadding actions={canManage ? <ITButton label={t("new")} onClick={() => setEditing({ warehouse: null })} /> : undefined}>
			<ITDataTable<Warehouse>
				defaultItemsPerPage={25}
				itemsPerPageOptions={[25, 50, 100]}
				layout="fixed"
				density="compact"
				virtualized
				virtualizedMaxHeight={400}
				rowHeight={50}
				showViewToggle={false}
 columns={columns}
 fetchData={(params) => warehouseApi.query(params as TableParams)}
 reloadTrigger={reload}
 onRowClick={(warehouse) => navigate(`/warehouses/${warehouse.id}`)}
			/>
			<WarehouseDialog
				isOpen={!!editing}
				warehouse={editing?.warehouse ?? null}
				onClose={() => setEditing(null)}
				onSaved={(saved) => {
					const created = !editing?.warehouse;
					setEditing(null);
					toast(t("common:saved"));
					if (created) navigate(`/warehouses/${saved.id}`);
					else refresh();
				}}
			/>
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(`actions.${confirm.kind}`) : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { name: confirm.warehouse.name }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivate" ? "danger" : "primary"}
			/>
		</ITPage>
	);
}
