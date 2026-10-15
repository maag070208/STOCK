import { type Column, ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { IconButton } from "@shared/ui/IconButton";
import { FaEdit } from "react-icons/fa";
import { printerApi, type Printer } from "@entities/label";
import { useCan } from "@entities/user";
import { warehouseApi } from "@entities/warehouse";
import { PrinterDialog, type WarehouseOption } from "@features/label/PrinterDialog";
import { toast } from "@app/store";
import { PlainTable } from "@shared/ui/PlainTable";

// The printers of each warehouse, with their addresses: only administrators see them (labels.md section 5). The screen
// is built with the components of the UI kit only (web.md section 8).
export function PrintersPage() {
	const navigate = useNavigate();
	const { t } = useTranslation(["labels", "common"]);
	const canManage = useCan("printers.manage");
	const [printers, setPrinters] = useState<Printer[] | null>(null);
	const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
	const [editing, setEditing] = useState<{ printer: Printer | null } | null>(null);

	const load = useCallback(async () => {
		setPrinters(await printerApi.list().catch(() => []));
	}, []);

	useEffect(() => {
		void load();
		if (canManage) {
			void warehouseApi
				.query({ page: 1, limit: 200, filters: { active: "true" } })
				.then((page) => setWarehouses(page.data.map((w) => ({ id: w.id, code: w.code, name: w.name }))))
				.catch(() => setWarehouses([]));
		}
	}, [load, canManage]);

	const columns: Column<Printer>[] = [
		{ key: "name", label: t("printers.columns.name"), type: "string", filter: "search" },
		{ key: "warehouseCode", label: t("printers.columns.warehouse"), type: "string", filter: "search", render: (printer) => printer.warehouseCode ?? "" },
		{ key: "ipAddress", label: t("printers.columns.ipAddress"), type: "string" },
		{ key: "port", label: t("printers.columns.port"), type: "string" },
		{
			key: "active",
			label: t("printers.columns.status"),
			type: "string",
			render: (printer) => <ITBadget color={printer.active ? "success" : "gray"} label={printer.active ? t("common:status.active") : t("common:status.inactive")} />,
		},
		{
			key: "actions",
			label: "",
			type: "actions",
			actions: (printer) => (canManage ? <IconButton icon={<FaEdit aria-hidden />} label={t("common:actions.edit")} onClick={() => setEditing({ printer })} /> : null),
		},
	];

	return (
		<ITPage breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("printers.title") })}
			title={t("printers.title")}
			description={t("printers.description")}
			noPadding
			loading={printers === null}
			actions={canManage ? <ITButton label={t("printers.new")} onClick={() => setEditing({ printer: null })} /> : undefined}
		>
			<PlainTable<Printer>columns={columns} data={printers ?? []} size="sm" />
			<PrinterDialog
				printer={editing?.printer ?? null}
				warehouses={warehouses}
				isOpen={!!editing}
				onClose={() => setEditing(null)}
				onSaved={() => {
					setEditing(null);
					toast(t("common:saved"));
					void load();
				}}
			/>
		</ITPage>
	);
}
