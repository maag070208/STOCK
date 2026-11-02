import { type Column, type ColumnFilters, ITBadget, ITButton, ITCard, ITDataTable, ITPage, ITStack } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { orderApi, type Order, type OrderAlert, type OrderStatus } from "@entities/order";
import { useCan } from "@entities/user";
import type { TableParams } from "@shared/api/table";
import { dyn } from "@shared/i18n";
import { formatDate } from "@shared/lib/format";
import { cardBreakpoint, tableHeight } from "@shared/ui/tableLayout";
import { BentoStats, useCounts, type BentoStat } from "@shared/ui/BentoStats";

// The color of each status of an order (order-fulfillment.md section 6): created is waiting, validated is final.
export const STATUS_COLOR: Record<OrderStatus, "gray" | "warning" | "info" | "success" | "danger"> = {
	CREATED: "gray",
	FULFILLING: "warning",
	FULFILLED: "info",
	VALIDATED: "success",
	CANCELLED: "danger",
};

// The orders the person can see: every one with ALL, those of their warehouses with AREA (the API decides). The alerts
// of the day come first, so an order about to be due is not missed (section 7a).
export function OrdersPage() {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const navigate = useNavigate();
	const canCreate = useCan("orders.create");
	const [statuses, setStatuses] = useState<CatalogItem[]>([]);
	const [alerts, setAlerts] = useState<OrderAlert[]>([]);

	useEffect(() => {
		void catalogApi.items("ORDER_STATUS").then(setStatuses).catch(() => setStatuses([]));
		void orderApi
			.alerts()
			.then((answer) => setAlerts(answer.alerts))
			.catch(() => setAlerts([]));
	}, []);

	const [status, setStatus] = useState("");
	const filters = useMemo(() => (status ? { status } : {}) as ColumnFilters, [status]);
	const countOf = (code: string) => () => orderApi.query({ page: 1, limit: 1, filters: code ? { status: code } : {} });
	const counts = useCounts({ all: countOf(""), CREATED: countOf("CREATED"), FULFILLING: countOf("FULFILLING"), FULFILLED: countOf("FULFILLED"), VALIDATED: countOf("VALIDATED") });
	// The numbers are the filters of the list: each tile shows only its orders.
	const tile = (code: "all" | OrderStatus, label: string, tone: BentoStat["tone"]): BentoStat => {
		const value = code === "all" ? "" : code;
		return { id: code, label, value: counts[code as keyof typeof counts], tone, active: status === value, onClick: () => setStatus(value) };
	};
	const stats = [
		tile("all", t("common:stats.allOrders"), "ink"),
		tile("CREATED", t("status.CREATED"), "orange"),
		tile("FULFILLING", t("status.FULFILLING"), "amber"),
		tile("FULFILLED", t("status.FULFILLED"), "sand"),
		tile("VALIDATED", t("status.VALIDATED"), "plain"),
	];

	const columns: Column<Order>[] = [
		{ key: "number", label: t("columns.number"), type: "string", filter: "search", sortable: true },
		{ key: "customer", label: t("columns.customer"), type: "string", render: (order) => order.customer.name ?? "" },
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", render: (order) => order.warehouse.code ?? "" },
		{ key: "dueDate", label: t("columns.dueDate"), type: "string", sortable: true, render: (order) => formatDate(order.dueDate, i18n.language) },
		{
			key: "status",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: statuses.map((status) => ({ id: status.code, name: status.label ?? status.code })) },
			render: (order) => <ITBadget color={STATUS_COLOR[order.status.code]} label={order.status.label ?? t(dyn(`status.${order.status.code}`))} />,
		},
		{ key: "operator", label: t("columns.operator"), type: "string", render: (order) => order.operator?.name ?? t("detail.noOperator") },
	];

	return (
		<ITPage breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title") })} title={t("title")} description={t("description")} noPadding actions={canCreate ? <ITButton label={t("new")} onClick={() => navigate("/orders/new")} /> : undefined}>
			<BentoStats items={stats} label={t("common:stats.label")} />
			{alerts.length > 0 && (
				<ITCard tone="warning" title={t("alerts.title")} className="sf:mb-4">
					<ITStack spacing={1}>
					{alerts.map((alert) => (
						<ITButton
							key={alert.id}
							variant="outlined"
							label={`${alert.order.number} · ${alert.order.customer.name ?? ""} · ${t(dyn(`alerts.${alert.kind}`))} · ${formatDate(alert.order.dueDate, i18n.language)}`}
							onClick={() => navigate(`/orders/${alert.order.id}`)}
							className="sf:justify-start sf:text-left"
						/>
					))}
					</ITStack>
				</ITCard>
			)}
			<ITDataTable<Order>
				defaultItemsPerPage={25}
				itemsPerPageOptions={[25, 50, 100]}
				layout="fixed"
				density="compact"
				virtualized
				virtualizedMaxHeight={tableHeight()}
				rowHeight={50}
				showViewToggle={false}
				autoCardBreakpoint={cardBreakpoint()} columns={columns} externalFilters={filters} fetchData={(params) => orderApi.query(params as TableParams)} onRowClick={(order) => navigate(`/orders/${order.id}`)}
			/>
		</ITPage>
	);
}
