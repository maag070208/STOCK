import { type Column, ITDataTable, ITPage, ITText } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { IconButton } from "@shared/ui/IconButton";
import { FaBalanceScale } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { receivingApi, type Discrepancy } from "@entities/invoice";
import { AdjustDialog } from "@features/invoice/InvoiceDialogs";
import type { TableParams } from "@shared/api/table";
import { formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";
import { cardBreakpoint, tableHeight } from "@shared/ui/tableLayout";
import { BentoStats, type BentoStat } from "@shared/ui/BentoStats";

// The open discrepancies the person can resolve (F4-21): OFFICE and ADMIN in any warehouse, a manager in theirs.
// Each one is adjusted to what was counted, with a note.
export function DiscrepanciesPage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const navigate = useNavigate();
	const [reload, setReload] = useState(0);
	const [adjusting, setAdjusting] = useState<Discrepancy | null>(null);
	const number = (value: number) => formatQuantity(value, 3, i18n.language).replace(/[.,]?0+$/, "");
	const signed = (value: number) => `${value > 0 ? "+" : ""}${number(value)}`;

	// The open ones are few by nature: the first page of the API holds them, and tells the shortages from the surpluses.
	const [open, setOpen] = useState<Discrepancy[] | null>(null);
	useEffect(() => {
		void receivingApi
			.discrepancies({ page: 1, limit: 200, filters: {} })
			.then((page) => setOpen(page.data))
			.catch(() => setOpen(null));
	}, [reload]);
	const stats: BentoStat[] = [
		{ id: "open", label: t("common:stats.open"), value: open?.length ?? null, tone: open?.length ? "amber" : "plain" },
		{ id: "short", label: t("common:stats.short"), value: open?.filter((row) => row.difference < 0).length ?? null },
		{ id: "over", label: t("common:stats.over"), value: open?.filter((row) => row.difference > 0).length ?? null },
	];

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
			actions: (row) => <IconButton icon={<FaBalanceScale aria-hidden />} label={t("detail.adjust")} variant="outlined" onClick={() => setAdjusting(row)} />,
		},
	];

	return (
		<ITPage title={t("discrepancies.title")} description={t("discrepancies.description")} breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("discrepancies.title") })} noPadding>
			<BentoStats items={stats} label={t("common:stats.label")} />
			<ITDataTable<Discrepancy>
				defaultItemsPerPage={25}
				itemsPerPageOptions={[25, 50, 100]}
				layout="fixed"
				density="compact"
				virtualized
				virtualizedMaxHeight={tableHeight()}
				rowHeight={50}
				showViewToggle={false}
				autoCardBreakpoint={cardBreakpoint()}
 columns={columns}
 fetchData={(params) => receivingApi.discrepancies(params as TableParams)}
 reloadTrigger={reload}
 onRowClick={(row) => navigate(`/invoices/${row.invoice.id}`)}
			/>
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
