import { type Column, type ColumnFilters, ITDataTable, ITGrid, ITPage, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { reportApi, warehouseOptionsOf, type InvoiceReportPage as InvoicesAnswer, type InvoiceReportRow, type ReportRef } from "@entities/report";
import { formatDate, formatQuantity } from "@shared/lib/format";
import { DateRangeFilter, ExportButtons, PartnerFilter, ReportError, ReportTotals, WarehouseFilter, periodOf, totalEntries } from "./ReportParts";
import { ReportTabs } from "./ReportTabs";

// The invoices and entries of the warehouses the reader reaches (reports.md section 3): what was expected, counted and
// placed, by period, supplier and status, with the counted quantity of each status.
export function InvoicesReportPage() {
	const { t, i18n } = useTranslation(["reports", "common"]);
	const [answer, setAnswer] = useState<InvoicesAnswer | null>(null);
	const [filters, setFilters] = useState<ColumnFilters>({});
	const [error, setError] = useState<string | null>(null);
	const [warehouses, setWarehouses] = useState<ReportRef[]>([]);
	const [statuses, setStatuses] = useState<CatalogItem[]>([]);
	const [warehouseId, setWarehouseId] = useState("");
	const [supplierId, setSupplierId] = useState("");
	const [from, setFrom] = useState<Date | null>(null);
	const [to, setTo] = useState<Date | null>(null);

	useEffect(() => {
		void catalogApi.items("INVOICE_STATUS").then(setStatuses).catch(() => setStatuses([]));
	}, []);

	const chosen = { ...(warehouseId ? { warehouseId } : {}), ...(supplierId ? { supplierId } : {}), ...periodOf(from, to) };
	const reload = `${warehouseId}|${supplierId}|${from?.toISOString() ?? ""}|${to?.toISOString() ?? ""}`;
	const number = (value: number) => formatQuantity(value, null, i18n.language);

	const columns: Column<InvoiceReportRow>[] = [
		{ key: "number", label: t("columns.invoice"), type: "string", sortable: true, render: (row) => row.invoice.code ?? "" },
		{ key: "supplier", label: t("columns.supplier"), type: "string", sortable: true, render: (row) => row.supplier.name ?? row.supplier.code ?? "" },
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", render: (row) => row.warehouse.code ?? "" },
		{
			key: "status",
			label: t("columns.status"),
			type: "catalog",
			sortable: true,
			filter: "catalog",
			catalogOptions: { data: statuses.map((status) => ({ id: status.code, name: status.label ?? status.code })) },
			render: (row) => row.statusLabel ?? row.status ?? "",
		},
		{ key: "date", label: t("columns.date"), type: "string", sortable: true, render: (row) => formatDate(row.invoiceDate, i18n.language) },
		{ key: "product", label: t("columns.product"), type: "string", render: (row) => `${row.product.code ?? ""} · ${row.product.name ?? ""}` },
		{ key: "expected", label: t("columns.expected"), type: "string", render: (row) => number(row.expected) },
		{ key: "counted", label: t("columns.counted"), type: "string", render: (row) => number(row.counted) },
		{ key: "difference", label: t("columns.difference"), type: "string", render: (row) => number(row.difference) },
		{ key: "placed", label: t("columns.placed"), type: "string", render: (row) => number(row.placed) },
	];

	return (
		<ITPage title={t("invoices.title")} description={t("invoices.description")} noPadding actions={<ExportButtons kind="invoices" filters={filters} />}>
			<ReportTabs />
			<ITGrid container spacing={3} className="sf:mb-4 sf:items-end">
				<ITGrid item xs={12} md={4}>
					<WarehouseFilter options={warehouses} value={warehouseId} onChange={setWarehouseId} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<PartnerFilter kind="suppliers" value={supplierId} onChange={setSupplierId} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<DateRangeFilter from={from} to={to} onFrom={setFrom} onTo={setTo} />
				</ITGrid>
			</ITGrid>
			<ReportError message={error} />
			{answer && (
				<ReportTotals
					label={t("totals.label")}
					testId="invoices-total"
					entries={[
						{ key: "expected", label: t("totals.expected"), value: number(answer.expected) },
						{ key: "counted", label: t("totals.counted"), value: number(answer.counted) },
						{ key: "difference", label: t("totals.difference"), value: number(answer.difference) },
						{ key: "placed", label: t("totals.placed"), value: number(answer.placed) },
						...totalEntries(answer.statusTotals, number),
					]}
				/>
			)}
			<ITText as="section" data-testid="invoices-report">
				<ITDataTable<InvoiceReportRow>
					defaultItemsPerPage={25}
					itemsPerPageOptions={[25, 50, 100]}
					layout="fixed"
					density="compact"
					virtualized
					virtualizedMaxHeight={400}
					rowHeight={50}
					showViewToggle={false}
 columns={columns}
 // The filters this screen manages travel as external filters: the table merges them with its own
 // before asking, and the export takes exactly the filters of the last answer.
 externalFilters={chosen}
 fetchData={(params) => {
 setFilters(params.filters);
 return reportApi
 .invoices({ page: params.page, limit: params.limit, filters: params.filters, sort: params.sort })
 .then((page) => {
 setAnswer(page);
 setError(null);
 setWarehouses((current) => warehouseOptionsOf(current, page.data.map((row) => row.warehouse)));
 return page;
 })
 .catch((failure: Error) => {
 setError(failure.message);
 return { data: [], total: 0 };
 });
 }}
 reloadTrigger={reload}
				/>
			</ITText>
		</ITPage>
	);
}
