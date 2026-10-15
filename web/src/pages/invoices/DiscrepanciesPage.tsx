import { ITButton, ITDataTable, ITPage, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { receivingApi, type Discrepancy } from "@entities/invoice";
import { AdjustDialog } from "@features/invoice/InvoiceDialogs";
import type { TableParams } from "@shared/api/table";
import { formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";

// The open discrepancies the person can resolve (F4-21): OFFICE and ADMIN in any warehouse, a manager in theirs.
// Each one is adjusted to what was counted, with a note.
export function DiscrepanciesPage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const navigate = useNavigate();
	const [reload, setReload] = useState(0);
	const [adjusting, setAdjusting] = useState<Discrepancy | null>(null);
	const number = (value: number) => formatQuantity(value, 3, i18n.language).replace(/[.,]?0+$/, "");
	const signed = (value: number) => `${value > 0 ? "+" : ""}${number(value)}`;

	const columns: Column<Discrepancy>[] = [
		{ key: "invoice", label: t("discrepancies.columns.invoice"), type: "string", render: (row) => row.invoice.code ?? "" },
		{ key: "warehouse", label: t("discrepancies.columns.warehouse"), type: "string", render: (row) => row.warehouse.code ?? "" },
		{ key: "product", label: t("discrepancies.columns.product"), type: "string", render: (row) => `${row.product.code} · ${row.product.name}` },
		{ key: "operator", label: t("discrepancies.columns.operator"), type: "string", render: (row) => row.operator.name ?? "" },
		{ key: "expectedQuantity", label: t("discrepancies.columns.expected"), type: "string", render: (row) => <ITText className="sf:tabular-nums">{number(row.expectedQuantity)}</ITText> },
		{ key: "countedQuantity", label: t("discrepancies.columns.counted"), type: "string", render: (row) => <ITText className="sf:tabular-nums">{number(row.countedQuantity)}</ITText> },
		{ key: "difference", label: t("discrepancies.columns.difference"), type: "string", render: (row) => <ITText className="sf:tabular-nums sf:text-danger">{signed(row.difference)}</ITText> },
		{ key: "countNotes", label: t("discrepancies.columns.notes"), type: "string", render: (row) => row.countNotes ?? "" },
		{
			key: "actions",
			label: t("discrepancies.columns.actions"),
			type: "actions",
			actions: (row) => <ITButton size="sm" label={t("detail.adjust")} onClick={() => setAdjusting(row)} />,
		},
	];

	return (
		<ITPage title={t("discrepancies.title")} description={t("discrepancies.description")} breadcrumbs={[{ label: t("discrepancies.title") }]} noPadding>
			<section data-testid="discrepancies">
				<ITDataTable<Discrepancy>
					columns={columns}
					fetchData={(params) => receivingApi.discrepancies(params as TableParams)}
					reloadTrigger={reload}
					layout="fixed"
					onRowClick={(row) => navigate(`/invoices/${row.invoice.id}`)}
				/>
			</section>
			<AdjustDialog
				assignmentId={adjusting?.assignmentId ?? null}
				summary={adjusting && { expected: number(adjusting.expectedQuantity), counted: number(adjusting.countedQuantity), difference: signed(adjusting.difference) }}
				onClose={() => setAdjusting(null)}
				onSaved={() => {
					setAdjusting(null);
					toast(t("adjustDialog.done"));
					setReload((value) => value + 1);
				}}
			/>
		</ITPage>
	);
}
