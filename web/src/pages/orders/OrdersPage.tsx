import { ITBadget, ITButton, ITDataTable, ITPage, type Column } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { orderApi, type Order, type OrderAlert, type OrderStatus } from "@entities/order";
import { useCan } from "@entities/user";
import type { TableParams } from "@shared/api/table";
import { dyn } from "@shared/i18n";
import { formatDate } from "@shared/lib/format";

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
		<ITPage title={t("title")} description={t("description")} noPadding actions={canCreate ? <ITButton label={t("new")} onClick={() => navigate("/orders/new")} /> : undefined}>
			{alerts.length > 0 && (
				<section aria-label={t("alerts.title")} data-testid="order-alerts" className="sf:mb-4 sf:flex sf:flex-col sf:gap-1">
					<h2 className="sf:text-title-md">{t("alerts.title")}</h2>
					{alerts.map((alert) => (
						<button
							key={alert.id}
							type="button"
							onClick={() => navigate(`/orders/${alert.order.id}`)}
							className="sf:cursor-pointer sf:rounded-md sf:border sf:border-outline-variant sf:px-3 sf:py-2 sf:text-left sf:text-body-md"
							data-alert={alert.order.number}
						>
							{`${alert.order.number} · ${alert.order.customer.name ?? ""} · ${t(dyn(`alerts.${alert.kind}`))} · ${formatDate(alert.order.dueDate, i18n.language)}`}
						</button>
					))}
				</section>
			)}
			<ITDataTable<Order> columns={columns} fetchData={(params) => orderApi.query(params as TableParams)} layout="fixed" onRowClick={(order) => navigate(`/orders/${order.id}`)} />
		</ITPage>
	);
}
