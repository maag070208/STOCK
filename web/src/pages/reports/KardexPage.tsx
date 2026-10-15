import { ITDataTable, ITPage, ITText, type Column, type ColumnFilters } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ProductPicker, type Product } from "@entities/product";
import { kardexClosing, kardexRows, reportApi, warehouseOptionsOf, type KardexPage as KardexAnswer, type KardexRow, type ReportRef } from "@entities/report";
import { formatDateTime, formatQuantity } from "@shared/lib/format";
import { DateRangeFilter, ExportButtons, ReportError, ReportTotals, WarehouseFilter, periodOf } from "./ReportParts";
import { ReportTabs } from "./ReportTabs";

// The kardex of one material in one warehouse (reports.md section 3): its movements in order, with the running balance of
// the location and of the warehouse as the API computed them. The screen never adds quantities up.
export function KardexPage() {
	const { t, i18n } = useTranslation(["reports", "common"]);
	const [answer, setAnswer] = useState<KardexAnswer | null>(null);
	const [filters, setFilters] = useState<ColumnFilters>({});
	const [error, setError] = useState<string | null>(null);
	const [warehouses, setWarehouses] = useState<ReportRef[]>([]);
	const [product, setProduct] = useState<Product | null>(null);
	const [warehouseId, setWarehouseId] = useState("");
	const [from, setFrom] = useState<Date | null>(null);
	const [to, setTo] = useState<Date | null>(null);

	// The kardex is of one material in one warehouse, and the report asks for both (reports.md section 3): the warehouses
	// where that material moved are read from the movements report, so nothing outside the reader's scope is offered.
	useEffect(() => {
		if (!product) {
			setWarehouses([]);
			setWarehouseId("");
			return;
		}
		let cancelled = false;
		void reportApi
			.movements({ page: 1, limit: 200, filters: { productId: product.id } })
			.then((page) => {
				if (cancelled) return;
				const options = warehouseOptionsOf(page.data.map((movement) => movement.warehouse));
				setWarehouses(options);
				setWarehouseId((current) => (current && options.some((option) => option.id === current) ? current : options.length === 1 ? options[0].id : ""));
			})
			.catch(() => {
				if (!cancelled) setWarehouses([]);
			});
		return () => {
			cancelled = true;
		};
	}, [product]);

	const chosen: ColumnFilters = { ...(product ? { productId: product.id } : {}), ...(warehouseId ? { warehouseId } : {}), ...periodOf(from, to) };
	// The filters the screen manages: the table reads again when one of them changes.
	const reload = `${product?.id ?? ""}|${warehouseId}|${from?.toISOString() ?? ""}|${to?.toISOString() ?? ""}`;
	const decimals = answer?.product?.unit?.decimalPlaces ?? product?.unit.decimalPlaces ?? 0;
	const number = (value: number | null) => (value === null ? "" : formatQuantity(value, decimals, i18n.language));

	const columns: Column<KardexRow>[] = [
		{ key: "occurredAt", label: t("columns.date"), type: "string", render: (row) => formatDateTime(row.occurredAt, i18n.language) },
		{ key: "type", label: t("columns.type"), type: "string" },
		{ key: "location", label: t("columns.location"), type: "string" },
		{ key: "incoming", label: t("columns.incoming"), type: "string", render: (row) => number(row.incoming) },
		{ key: "outgoing", label: t("columns.outgoing"), type: "string", render: (row) => number(row.outgoing) },
		{
			key: "runningLocation",
			label: t("columns.runningLocation"),
			type: "string",
			render: (row) => <span data-running={row.runningLocation ?? ""}>{number(row.runningLocation)}</span>,
		},
		{
			key: "runningWarehouse",
			label: t("columns.runningWarehouse"),
			type: "string",
			render: (row) => <span data-running={row.runningWarehouse ?? ""}>{number(row.runningWarehouse)}</span>,
		},
		{ key: "user", label: t("columns.user"), type: "string" },
		{ key: "reason", label: t("columns.reason"), type: "string" },
	];

	const closing = kardexClosing(answer);

	return (
		<ITPage title={t("kardex.title")} description={t("kardex.description")} noPadding actions={<ExportButtons kind="kardex" filters={filters} />}>
			<ReportTabs />
			<div className="sf:mb-4 sf:grid sf:grid-cols-1 sf:items-end sf:gap-3 sf:md:grid-cols-4">
				<ProductPicker name="productId" label={t("filters.product")} value={product?.id ?? ""} onChange={setProduct} required />
				<WarehouseFilter options={warehouses} value={warehouseId} onChange={setWarehouseId} required />
				<DateRangeFilter from={from} to={to} onFrom={setFrom} onTo={setTo} />
			</div>
			<ReportError message={error} />
			{(!product || !warehouseId) && <ITText className="sf:mb-4">{t("filters.chooseProduct")}</ITText>}
			{product && answer?.product && (
				<ReportTotals
					label={t("totals.label")}
					testId="kardex-closing"
					entries={[
						{
							key: "of",
							label: t("kardexOf", { product: answer.product.product.name ?? product.name, warehouse: answer.product.warehouse.code ?? "" }),
							value: "",
						},
						{ key: "closing", label: t("totals.closing"), value: closing === null ? "" : formatQuantity(closing, decimals, i18n.language) },
					]}
				/>
			)}
			<section data-testid="kardex-report">
				<ITDataTable<KardexRow>
					columns={columns}
					fetchData={(params) => {
						setFilters(chosen);
						// Without the material and its warehouse there is no kardex to ask for.
						if (!product || !warehouseId) {
							setAnswer(null);
							return Promise.resolve({ data: [], total: 0 });
						}
						return reportApi
							.kardex({ page: params.page, limit: params.limit, filters: chosen })
							.then((page) => {
								const list = kardexRows(page);
								setAnswer(page);
								setError(null);
								return { data: list, total: page.total };
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
