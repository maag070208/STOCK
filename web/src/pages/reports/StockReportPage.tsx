import { ITDataTable, ITPage, type Column, type ColumnFilters } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { reportApi, warehouseOptionsOf, type ReportRef, type StockReportPage as StockPage, type StockReportRow } from "@entities/report";
import { formatQuantity } from "@shared/lib/format";
import { BelowMinimumFilter, ExportButtons, ReportError, ReportTotals, WarehouseFilter, totalEntries } from "./ReportParts";
import { ReportTabs } from "./ReportTabs";

// The current stock (reports.md section 3): every balance of the warehouses the reader reaches, with the totals by
// warehouse, by aisle and rack, and the grand total, which is always the sum of the rows listed.
export function StockReportPage() {
	const { t, i18n } = useTranslation(["reports", "common"]);
	const [page, setPage] = useState<StockPage | null>(null);
	// The filters of the last answer: the export takes exactly these, so the file and the screen cannot disagree.
	const [filters, setFilters] = useState<ColumnFilters>({});
	const [error, setError] = useState<string | null>(null);
	const [warehouses, setWarehouses] = useState<ReportRef[]>([]);
	const [warehouseId, setWarehouseId] = useState("");
	const [belowMinimum, setBelowMinimum] = useState(false);

	// The filters the screen manages itself; the columns' own filters travel with the table.
	const external: ColumnFilters = { ...(warehouseId ? { warehouseId } : {}), ...(belowMinimum ? { belowMinimum: true } : {}) };
	const reload = `${warehouseId}|${belowMinimum}`;

	const columns: Column<StockReportRow>[] = [
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", sortable: true, render: (row) => row.warehouse.code ?? "" },
		{ key: "aisle", label: t("columns.aisle"), type: "string", filter: "search" },
		{ key: "rack", label: t("columns.rack"), type: "string", filter: "search" },
		{ key: "location", label: t("columns.location"), type: "string", filter: "search", sortable: true, render: (row) => row.location.code ?? "" },
		{ key: "product", label: t("columns.product"), type: "string", filter: "search", sortable: true, render: (row) => `${row.product.code ?? ""} · ${row.product.name ?? ""}` },
		{ key: "unit", label: t("columns.unit"), type: "string", render: (row) => row.unit?.label ?? row.unit?.code ?? "" },
		{ key: "quantity", label: t("columns.quantity"), type: "string", sortable: true, render: (row) => formatQuantity(row.quantity, row.unit?.decimalPlaces, i18n.language) },
		{ key: "minimum", label: t("columns.minimum"), type: "string", render: (row) => formatQuantity(row.minimum, row.unit?.decimalPlaces, i18n.language) },
		{
			key: "shortfall",
			label: t("columns.shortfall"),
			type: "string",
			render: (row) => (row.shortfall === null ? "" : formatQuantity(row.shortfall, row.unit?.decimalPlaces, i18n.language)),
		},
	];

	return (
		<ITPage title={t("stock.title")} description={t("stock.description")} noPadding actions={<ExportButtons kind="stock" filters={filters} />}>
			<ReportTabs />
			<div className="sf:mb-4 sf:flex sf:flex-wrap sf:items-end sf:gap-3">
				<WarehouseFilter options={warehouses} value={warehouseId} onChange={setWarehouseId} />
				<BelowMinimumFilter value={belowMinimum} onChange={setBelowMinimum} />
			</div>
			<ReportError message={error} />
			{page && (
				<ReportTotals
					label={t("totals.label")}
					testId="stock-total"
					entries={[
						{ key: "quantity", label: t("totals.quantity"), value: formatQuantity(page.quantity, null, i18n.language) },
						...totalEntries(page.warehouseTotals, (quantity) => formatQuantity(quantity, null, i18n.language)),
					]}
				/>
			)}
			<section data-testid="stock-report">
				<ITDataTable<StockReportRow>
					columns={columns}
					// The filters this screen manages travel as external filters: the table merges them with its own
					// before asking, and the export takes exactly the filters of the last answer.
					externalFilters={external}
					fetchData={(params) => {
						setFilters(params.filters);
						return reportApi
							.stock({ page: params.page, limit: params.limit, filters: params.filters, sort: params.sort })
							.then((answer) => {
								setPage(answer);
								setError(null);
								setWarehouses((current) => warehouseOptionsOf(current, answer.data.map((row) => row.warehouse)));
								return answer;
							})
							.catch((failure: Error) => {
								setError(failure.message);
								return { data: [], total: 0 };
							});
					}}
					reloadTrigger={reload}
					layout="fixed"
				/>
			</section>
		</ITPage>
	);
}
