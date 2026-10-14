import { ITBadget, ITButton, ITDataTable, ITPage, type Column } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { invoiceApi, type Invoice, type InvoiceStatus } from "@entities/invoice";
import { useCan } from "@entities/user";
import type { TableParams } from "@shared/api/table";
import { formatDate } from "@shared/lib/format";

// The color of each status of an invoice: open is still being captured, closed is done.
export const STATUS_COLOR: Record<InvoiceStatus, "gray" | "warning" | "info" | "success"> = { OPEN: "gray", PENDING: "warning", IN_ASSIGNMENT: "info", CLOSED: "success" };

// The invoices the person can see: every one with ALL, those of their warehouses with AREA (the API decides).
export function InvoicesPage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const navigate = useNavigate();
	const canCreate = useCan("invoices.create");
	const [statuses, setStatuses] = useState<CatalogItem[]>([]);

	useEffect(() => {
		void catalogApi.items("INVOICE_STATUS").then(setStatuses).catch(() => setStatuses([]));
	}, []);

	const columns: Column<Invoice>[] = [
		{ key: "number", label: t("columns.number"), type: "string", filter: "search", sortable: true },
		{ key: "supplier", label: t("columns.supplier"), type: "string", render: (invoice) => invoice.supplier.name ?? "" },
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", render: (invoice) => invoice.warehouse.code ?? "" },
		{ key: "invoiceDate", label: t("columns.date"), type: "string", sortable: true, render: (invoice) => formatDate(invoice.invoiceDate, i18n.language) },
		{
			key: "status",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: statuses.map((s) => ({ id: s.code, name: s.label ?? s.code })) },
			render: (invoice) => <ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />,
		},
	];

	return (
		<ITPage title={t("title")} description={t("description")} noPadding actions={canCreate ? <ITButton label={t("new")} onClick={() => navigate("/invoices/new")} /> : undefined}>
			<ITDataTable<Invoice> columns={columns} fetchData={(params) => invoiceApi.query(params as TableParams)} layout="fixed" onRowClick={(invoice) => navigate(`/invoices/${invoice.id}`)} />
		</ITPage>
	);
}
