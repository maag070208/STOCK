import { ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { printerApi, type Printer } from "@entities/label";
import { useCan } from "@entities/user";
import { warehouseApi } from "@entities/warehouse";
import { PrinterDialog, type WarehouseOption } from "@features/label/PrinterDialog";
import { toast } from "@app/store";

// The printers of each warehouse, with their addresses: only administrators see them (labels.md section 5).
export function PrintersPage() {
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

	return (
		<ITPage
			title={t("printers.title")}
			description={t("printers.description")}
			noPadding
			loading={printers === null}
			actions={canManage ? <ITButton label={t("printers.new")} onClick={() => setEditing({ printer: null })} /> : undefined}
		>
			<table className="sf:w-full sf:text-body-md" data-testid="printers">
				<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
					<tr>
						<th className="sf:p-3 sf:text-left">{t("printers.columns.name")}</th>
						<th className="sf:p-3 sf:text-left">{t("printers.columns.warehouse")}</th>
						<th className="sf:p-3 sf:text-left">{t("printers.columns.ipAddress")}</th>
						<th className="sf:p-3 sf:text-right">{t("printers.columns.port")}</th>
						<th className="sf:p-3 sf:text-left">{t("printers.columns.status")}</th>
						<th className="sf:p-3" />
					</tr>
				</thead>
				<tbody>
					{(printers ?? []).map((printer) => (
						<tr key={printer.id} className="sf:border-t sf:border-outline" data-printer={printer.id}>
							<td className="sf:p-3">{printer.name}</td>
							<td className="sf:p-3 sf:text-code-sm">{printer.warehouseCode}</td>
							<td className="sf:p-3 sf:font-mono sf:text-code-sm">{printer.ipAddress}</td>
							<td className="sf:p-3 sf:text-right">{printer.port}</td>
							<td className="sf:p-3">
								<ITBadget color={printer.active ? "success" : "gray"} label={printer.active ? t("common:status.active") : t("common:status.inactive")} />
							</td>
							<td className="sf:p-3 sf:text-right">{canManage && <ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => setEditing({ printer })} />}</td>
						</tr>
					))}
				</tbody>
			</table>
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
