import { type Column, ITButton, ITDataTable, ITPage, ITTabs, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { stockApi, type Balance, type ProductTotal } from "@entities/stock";
import { useCan } from "@entities/user";
import { warehouseApi } from "@entities/warehouse";
import type { TableParams } from "@shared/api/table";
import { formatQuantity } from "@shared/lib/format";

// Stock, read-only (F4-19): by location (each balance, with the groups by aisle and rack as filters) and by material
// in each warehouse. The API keeps it inside the reader's warehouses.
export function StockPage() {
	const { t, i18n } = useTranslation(["stock", "common"]);
	const navigate = useNavigate();
	const canOpen = useCan("stock.opening");
	const [warehouses, setWarehouses] = useState<{ id: string; name: string }[]>([]);

	useEffect(() => {
		void warehouseApi
			.query({ page: 1, limit: 200, filters: {} })
			.then((page) => setWarehouses(page.data.map((w) => ({ id: w.id, name: w.code }))))
			.catch(() => setWarehouses([]));
	}, []);

	const quantity = (row: Balance | ProductTotal) => (
		<ITText as="span" className="sf:block sf:text-right sf:tabular-nums">
			{formatQuantity(row.quantity, row.unit?.decimalPlaces, i18n.language)}
		</ITText>
	);
	const warehouseColumn = {
		key: "warehouseId",
		label: t("columns.warehouse"),
		type: "catalog" as const,
		filter: "catalog" as const,
		catalogOptions: { data: warehouses },
		render: (row: Balance | ProductTotal) => row.warehouseCode,
	};

	const byLocation: Column<Balance>[] = [
		warehouseColumn,
		{ key: "location", label: t("columns.location"), type: "string", filter: "search", sortable: true, render: (row) => (row.temporary ? `${row.locationCode} · ${t("temporary")}` : row.locationCode) },
		{ key: "aisle", label: t("columns.aisle"), type: "string", filter: "search", render: (row) => row.aisle ?? "" },
		{ key: "rack", label: t("columns.rack"), type: "string", filter: "search", render: (row) => row.rack ?? "" },
		{ key: "product", label: t("columns.product"), type: "string", filter: "search", sortable: true, render: (row) => `${row.productCode} · ${row.productName}` },
		{ key: "unit", label: t("columns.unit"), type: "string", render: (row) => row.unit?.label ?? row.unit?.code ?? "" },
		{ key: "quantity", label: t("columns.quantity"), type: "string", sortable: true, render: quantity },
	];

	const byProduct: Column<ProductTotal>[] = [
		{ key: "product", label: t("columns.product"), type: "string", filter: "search", sortable: true, render: (row) => `${row.productCode} · ${row.productName}` },
		warehouseColumn,
		{ key: "unit", label: t("columns.unit"), type: "string", render: (row) => row.unit?.label ?? row.unit?.code ?? "" },
		{ key: "locations", label: t("columns.locations"), type: "string", sortable: true, render: (row) => (
						<ITText as="span" className="sf:block sf:text-right">
							{row.locations}
						</ITText>
					) },
		{ key: "quantity", label: t("columns.quantity"), type: "string", sortable: true, render: quantity },
	];

	return (
		<ITPage
			title={t("title")}
			description={t("description")}
			noPadding
			actions={canOpen ? <ITButton variant="outlined" label={t("opening.button")} onClick={() => navigate("/stock/opening")} /> : undefined}
		>
			<ITTabs
				variant="line"
				items={[
					{
						id: "byLocation",
						label: t("tabs.byLocation"),
						content: (
							<ITText as="div" data-testid="stock-by-location">
								<ITDataTable<Balance> showViewToggle={false} columns={byLocation} fetchData={(params) => stockApi.balances(params as TableParams)} layout="fixed" />
							</ITText>
						),
					},
					{
						id: "byProduct",
						label: t("tabs.byProduct"),
						content: (
							<ITText as="div" data-testid="stock-by-product">
								<ITDataTable<ProductTotal> columns={byProduct} fetchData={(params) => stockApi.products(params as TableParams)} layout="fixed" />
							</ITText>
						),
					},
				]}
			/>
		</ITPage>
	);
}
