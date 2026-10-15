import { ITDataTable, ITGrid, ITPage, ITText, type Column, type ColumnFilters } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { ProductPicker, type Product } from "@entities/product";
import { reportApi, warehouseOptionsOf, type MovementReportRow, type MovementsPage as MovementsAnswer, type ReportRef } from "@entities/report";
import { formatDateTime, formatQuantity } from "@shared/lib/format";
import { DateRangeFilter, ExportButtons, ReportError, ReportTotals, WarehouseFilter, periodOf, totalEntries } from "./ReportParts";
import { ReportTabs } from "./ReportTabs";

// The movements of the warehouses the reader reaches (reports.md section 3): what came in, what went out, by period,
// type, material and warehouse, with the totals of each.
export function MovementsReportPage() {
	const { t, i18n } = useTranslation(["reports", "common"]);
	const [answer, setAnswer] = useState<MovementsAnswer | null>(null);
	const [filters, setFilters] = useState<ColumnFilters>({});
	const [error, setError] = useState<string | null>(null);
	const [warehouses, setWarehouses] = useState<ReportRef[]>([]);
	const [types, setTypes] = useState<CatalogItem[]>([]);
	const [warehouseId, setWarehouseId] = useState("");
	const [product, setProduct] = useState<Product | null>(null);
	const [from, setFrom] = useState<Date | null>(null);
	const [to, setTo] = useState<Date | null>(null);

	useEffect(() => {
		void catalogApi.items("MOVEMENT_TYPE").then(setTypes).catch(() => setTypes([]));
	}, []);

	const chosen = { ...(warehouseId ? { warehouseId } : {}), ...(product ? { productId: product.id } : {}), ...periodOf(from, to) };
	const reload = `${warehouseId}|${product?.id ?? ""}|${from?.toISOString() ?? ""}|${to?.toISOString() ?? ""}`;
	const number = (value: number) => formatQuantity(value, null, i18n.language);

	const columns: Column<MovementReportRow>[] = [
		{ key: "date", label: t("columns.date"), type: "string", sortable: true, render: (row) => formatDateTime(row.occurredAt, i18n.language) },
		{
			key: "type",
			label: t("columns.type"),
			type: "catalog",
			sortable: true,
			filter: "catalog",
			catalogOptions: { data: types.map((type) => ({ id: type.code, name: type.label ?? type.code })) },
			render: (row) => row.typeLabel ?? row.type ?? "",
		},
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", sortable: true, render: (row) => row.warehouse.code ?? "" },
		{ key: "location", label: t("columns.location"), type: "string", render: (row) => row.location.code ?? "" },
		{ key: "product", label: t("columns.product"), type: "string", sortable: true, render: (row) => `${row.product.code ?? ""} · ${row.product.name ?? ""}` },
		{ key: "direction", label: t("columns.direction"), type: "string", render: (row) => (row.incoming ? t("directions.in") : t("directions.out")) },
		{ key: "quantity", label: t("columns.quantity"), type: "string", sortable: true, render: (row) => formatQuantity(row.quantity, row.unit?.decimalPlaces, i18n.language) },
		{ key: "user", label: t("columns.user"), type: "string", render: (row) => row.user?.name ?? row.user?.code ?? "" },
		{ key: "reason", label: t("columns.reason"), type: "string", render: (row) => row.reason ?? "" },
	];

	return (
		<ITPage title={t("movements.title")} description={t("movements.description")} noPadding actions={<ExportButtons kind="movements" filters={filters} />}>
			<ReportTabs />
			<ITGrid container spacing={3} className="sf:mb-4 sf:items-end">
				<ITGrid item xs={12} md={4}>
					<WarehouseFilter options={warehouses} value={warehouseId} onChange={setWarehouseId} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<ProductPicker name="productId" label={t("filters.product")} value={product?.id ?? ""} onChange={setProduct} />
				</ITGrid>
				<ITGrid item xs={12} md={4}>
					<DateRangeFilter from={from} to={to} onFrom={setFrom} onTo={setTo} />
				</ITGrid>
			</ITGrid>
			<ReportError message={error} />
			{answer && (
				<ReportTotals
					label={t("totals.label")}
					testId="movements-total"
					entries={[
						{ key: "incoming", label: t("totals.incoming"), value: number(answer.incoming) },
						{ key: "outgoing", label: t("totals.outgoing"), value: number(answer.outgoing) },
						...totalEntries(answer.typeTotals, number),
					]}
				/>
			)}
			<ITText as="section" data-testid="movements-report">
				<ITDataTable<MovementReportRow>
					columns={columns}
					// The filters this screen manages travel as external filters: the table merges them with its own
					// before asking, and the export takes exactly the filters of the last answer.
					externalFilters={chosen}
					fetchData={(params) => {
						setFilters(params.filters);
						return reportApi
							.movements({ page: params.page, limit: params.limit, filters: params.filters, sort: params.sort })
							.then((page) => {
								setAnswer(page);
								setError(null);
								setWarehouses((current) => warehouseOptionsOf(current, page.data.map((movement) => movement.warehouse)));
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
