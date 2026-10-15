import { ITDataTable, ITGrid, ITPage, ITText, type Column, type ColumnFilters } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { reportApi, warehouseOptionsOf, type OrderReportPage as OrdersAnswer, type OrderReportRow, type ReportRef } from "@entities/report";
import { formatDate, formatDateTime, formatQuantity } from "@shared/lib/format";
import { DateRangeFilter, ExportButtons, PartnerFilter, ReportError, ReportTotals, WarehouseFilter, periodOf, totalEntries } from "./ReportParts";
import { ReportTabs } from "./ReportTabs";

// The orders of the warehouses the reader reaches (reports.md section 3): what was asked, what was picked and what moved
// to or from another order, by period, customer and status, with the picked quantity of each status.
export function OrdersReportPage() {
	const { t, i18n } = useTranslation(["reports", "common"]);
	const [answer, setAnswer] = useState<OrdersAnswer | null>(null);
	const [filters, setFilters] = useState<ColumnFilters>({});
	const [error, setError] = useState<string | null>(null);
	const [warehouses, setWarehouses] = useState<ReportRef[]>([]);
	const [statuses, setStatuses] = useState<CatalogItem[]>([]);
	const [warehouseId, setWarehouseId] = useState("");
	const [customerId, setCustomerId] = useState("");
	const [from, setFrom] = useState<Date | null>(null);
	const [to, setTo] = useState<Date | null>(null);

	useEffect(() => {
		void catalogApi.items("ORDER_STATUS").then(setStatuses).catch(() => setStatuses([]));
	}, []);

	const chosen = { ...(warehouseId ? { warehouseId } : {}), ...(customerId ? { customerId } : {}), ...periodOf(from, to) };
	const reload = `${warehouseId}|${customerId}|${from?.toISOString() ?? ""}|${to?.toISOString() ?? ""}`;
	const number = (value: number) => formatQuantity(value, null, i18n.language);

	const columns: Column<OrderReportRow>[] = [
		{ key: "number", label: t("columns.order"), type: "string", sortable: true, render: (row) => row.order.code ?? "" },
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", render: (row) => row.warehouse.code ?? "" },
		{ key: "customer", label: t("columns.customer"), type: "string", sortable: true, render: (row) => row.customer.name ?? row.customer.code ?? "" },
		{
			key: "status",
			label: t("columns.status"),
			type: "catalog",
			sortable: true,
			filter: "catalog",
			catalogOptions: { data: statuses.map((status) => ({ id: status.code, name: status.label ?? status.code })) },
			render: (row) => row.statusLabel ?? row.status ?? "",
		},
		{ key: "dueDate", label: t("columns.dueDate"), type: "string", sortable: true, render: (row) => formatDate(row.dueDate, i18n.language) },
		{ key: "product", label: t("columns.product"), type: "string", render: (row) => `${row.product.code ?? ""} · ${row.product.name ?? ""}` },
		{ key: "asked", label: t("columns.asked"), type: "string", render: (row) => number(row.asked) },
		{ key: "picked", label: t("columns.picked"), type: "string", render: (row) => number(row.picked) },
		{ key: "movedOut", label: t("columns.movedOut"), type: "string", render: (row) => number(row.movedOut) },
		{ key: "movedIn", label: t("columns.movedIn"), type: "string", render: (row) => number(row.movedIn) },
		{ key: "createdAt", label: t("columns.createdAt"), type: "string", sortable: true, render: (row) => formatDateTime(row.createdAt, i18n.language) },
		{ key: "validatedAt", label: t("columns.validatedAt"), type: "string", render: (row) => formatDateTime(row.validatedAt, i18n.language) },
	];

	return (
		<ITPage title={t("orders.title")} description={t("orders.description")} noPadding actions={<ExportButtons kind="orders" filters={filters} />}>
			<ReportTabs />
			<ITGrid container spacing={3} className="sf:mb-4 sf:items-end">
				<ITGrid item xs={12} md={4}>
					<WarehouseFilter options={warehouses} value={warehouseId} onChange={setWarehouseId} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<PartnerFilter kind="customers" value={customerId} onChange={setCustomerId} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<DateRangeFilter from={from} to={to} onFrom={setFrom} onTo={setTo} />
				</ITGrid>
			</ITGrid>
			<ReportError message={error} />
			{answer && (
				<ReportTotals
					label={t("totals.label")}
					testId="orders-total"
					entries={[
						{ key: "asked", label: t("totals.asked"), value: number(answer.asked) },
						{ key: "picked", label: t("totals.picked"), value: number(answer.picked) },
						{ key: "movedOut", label: t("totals.movedOut"), value: number(answer.movedOut) },
						{ key: "movedIn", label: t("totals.movedIn"), value: number(answer.movedIn) },
						...totalEntries(answer.statusTotals, number),
					]}
				/>
			)}
			<ITText as="section" data-testid="orders-report">
				<ITDataTable<OrderReportRow>
				showViewToggle={false}
					columns={columns}
					// The filters this screen manages travel as external filters: the table merges them with its own
					// before asking, and the export takes exactly the filters of the last answer.
					externalFilters={chosen}
					fetchData={(params) => {
						setFilters(params.filters);
						return reportApi
							.orders({ page: params.page, limit: params.limit, filters: params.filters, sort: params.sort })
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
					layout="fixed"
				/>
			</ITText>
		</ITPage>
	);
}
