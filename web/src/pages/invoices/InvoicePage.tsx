import { ITAlert, ITBadget, ITButton, ITCard, ITEmptyState, ITFlex, ITGrid, ITPage, ITStack, ITStatCard, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { toast } from "@app/store";
import { captureSummary, invoiceApi, staffApi, type Assignment, type Invoice, type InvoiceLine, type StaffMember } from "@entities/invoice";
import { useCan } from "@entities/user";
import { AdjustDialog, AssignDialog } from "@features/invoice/InvoiceDialogs";
import { QuickCapture } from "@features/invoice/QuickCapture";
import { ApiError } from "@shared/api/client";
import { formatDate, formatQuantity } from "@shared/lib/format";
import { STATUS_COLOR } from "./InvoicesPage";
import { PlainTable } from "@shared/ui/PlainTable";

// One invoice: its header, its quick capture bar and its lines with the reception of each operator (F8-07). While
// nothing has been counted the invoice is open: materials are captured above the table and it can be closed by hand;
// from the first count on it is only read here. Only the components of the UI kit build the screen (web.md section 8).
export function InvoicePage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const { id = "" } = useParams();
	const location = useLocation();
	const navigate = useNavigate();
	const can = { create: useCan("invoices.create"), assign: useCan("receipts.assign"), resolve: useCan("discrepancies.resolve") };
	const [invoice, setInvoice] = useState<Invoice | null>(null);
	const [missing, setMissing] = useState(false);
	const [staff, setStaff] = useState<StaffMember[]>([]);
	const [assigning, setAssigning] = useState<InvoiceLine | null>(null);
	const [adjusting, setAdjusting] = useState<{ line: InvoiceLine; assignment: Assignment } | null>(null);
	const [closing, setClosing] = useState(false);
	const [refusal, setRefusal] = useState<string | null>(null);

	const load = useCallback(async () => {
		try {
			const found = await invoiceApi.get(id);
			setInvoice(found);
			if (can.assign) void staffApi.list(found.warehouse.id).then(setStaff).catch(() => setStaff([]));
		} catch {
			setMissing(true);
		}
	}, [id, can.assign]);

	useEffect(() => {
		void load();
	}, [load]);

	if (missing) {
		return (
			<ITPage
				title={t("title")}
				breadcrumbs={[{ label: t("title"), onClick: () => navigate("/invoices") }]}
				noPadding
				actions={<ITButton variant="outlined" label={t("detail.back")} onClick={() => navigate("/invoices")} />}
			>
				<ITAlert variant="error">{t("common:errors.notFound")}</ITAlert>
			</ITPage>
		);
	}

	// Open until something is counted: the fast flow captures its materials and closes it here.
	const open = invoice?.status.code === "OPEN" || invoice?.status.code === "PENDING";
	const lines = invoice?.lines ?? [];
	const summary = captureSummary(lines);
	const quantity = (value: number | null | undefined, line: InvoiceLine) => (value === null || value === undefined ? "" : formatQuantity(value, line.unit?.decimalPlaces, i18n.language));

	// Closing by hand is for the invoice that never had operators (F8-03): a refusal says why, in the API's words.
	async function closeInvoice() {
		setClosing(true);
		setRefusal(null);
		try {
			await invoiceApi.close(id);
			toast(t("closing.done"));
			await load();
		} catch (failure) {
			setRefusal(failure instanceof ApiError ? failure.message : t("common:errors.network"));
		} finally {
			setClosing(false);
		}
	}

	async function removeLine(line: InvoiceLine) {
		try {
			await invoiceApi.removeLine(id, line.id);
			toast(t("common:saved"));
			await load();
		} catch (failure) {
			setRefusal(failure instanceof ApiError ? failure.message : t("common:errors.network"));
		}
	}

	// The lines, and the reception of each one: what each operator has to receive, what they counted, and what they placed.
	const lineColumns: Column<InvoiceLine>[] = [
		{ key: "product", label: t("form.product"), type: "string", render: (line) => `${line.product.code ?? ""} · ${line.product.name ?? ""}` },
		{ key: "quantity", label: t("form.quantity"), type: "string", render: (line) => `${quantity(line.quantity, line)} ${line.unit?.label ?? ""}` },
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (line) => (
				<ITFlex gap={2} justify="end">
					{open && can.assign && !line.assignments.some((assignment) => assignment.countedQuantity !== null) && (
						<ITButton size="sm" variant="outlined" label={line.assignments.length ? t("detail.reassign") : t("detail.assign")} onClick={() => setAssigning(line)} />
					)}
					{open && can.create && <ITButton size="sm" variant="text" label={t("common:actions.remove")} onClick={() => void removeLine(line)} />}
				</ITFlex>
			),
		},
	];

	const received = lines.flatMap((line) => line.assignments.map((assignment) => ({ line, assignment })));
	const receptionColumns: Column<{ line: InvoiceLine; assignment: Assignment }>[] = [
		{ key: "operator", label: t("form.operator"), type: "string", render: (row) => row.assignment.operator.name ?? row.assignment.operator.code ?? "" },
		{ key: "material", label: t("form.product"), type: "string", render: (row) => row.line.product.code ?? "" },
		{ key: "expected", label: t("detail.expected"), type: "string", render: (row) => quantity(row.assignment.expectedQuantity, row.line) },
		{ key: "counted", label: t("detail.counted"), type: "string", render: (row) => quantity(row.assignment.countedQuantity, row.line) },
		{
			key: "outcome",
			label: t("columns.status"),
			type: "string",
			render: (row) =>
				row.assignment.outcome === null ? (
					<ITBadget color="gray" label={t("detail.notCounted")} />
				) : row.assignment.outcome === "MATCH" ? (
					<ITBadget color="success" label={t("detail.match")} />
				) : (
					<ITBadget color="danger" label={`${t("detail.discrepancy")} ${row.assignment.difference! > 0 ? "+" : ""}${quantity(row.assignment.difference, row.line)}`} />
				),
		},
		{ key: "placed", label: t("detail.placed"), type: "string", render: (row) => quantity(row.assignment.placedQuantity, row.line) },
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			// The material is assigned to a person, and a difference is settled, from the row that shows it.
			actions: (row) => (
				<ITFlex gap={2} justify="end">
					{open && can.assign && row.assignment.countedQuantity === null && (
						<ITButton size="sm" variant="outlined" label={t("detail.reassign")} onClick={() => setAssigning(row.line)} />
					)}
					{can.resolve && row.assignment.outcome === "DISCREPANCY" && invoice?.status.code === "PENDING" && (
						<ITButton size="sm" label={t("detail.adjust")} onClick={() => setAdjusting({ line: row.line, assignment: row.assignment })} />
					)}
				</ITFlex>
			),
		},
	];

	return (
		<ITPage
			title={invoice ? `${invoice.number} · ${invoice.supplier.name ?? ""}` : t("title")}
			breadcrumbs={[{ label: t("title"), onClick: () => navigate("/invoices") }, { label: invoice?.number ?? t("title") }]}
			loading={!invoice}
			noPadding
			actions={invoice && <ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />}
		>
			{invoice && (
				<ITStack spacing={6}>
					<ITGrid container spacing={4}>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.supplier")} value={`${invoice.supplier.code ?? ""} · ${invoice.supplier.name ?? ""}`} />
						</ITGrid>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.date")} value={formatDate(invoice.invoiceDate, i18n.language)} />
						</ITGrid>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.warehouse")} value={invoice.warehouse.code ?? ""} />
						</ITGrid>
					</ITGrid>

					{!open && <ITAlert variant="info">{t("closing.readOnly")}</ITAlert>}

					{can.create && open && (
						<ITCard title={t("capture.title")}>
							<QuickCapture invoiceId={id} lines={lines} autoFocus={(location.state as { focus?: string } | null)?.focus === "capture"} onSaved={() => void load()} />
						</ITCard>
					)}

					<ITStack spacing={3}>
						<ITFlex justify="between" align="center" wrap="wrap" gap={2}>
							<ITText as="h2" className="sf:text-title-lg">
								{t("detail.lines")}
							</ITText>
							<ITText muted>{`${t("capture.summaryLines", { count: summary.lines })} · ${t("capture.summaryPieces", { count: summary.pieces })}`}</ITText>
						</ITFlex>

						{lines.length === 0 ? (
							<ITEmptyState title={open ? t("capture.empty") : t("detail.noLines")} />
						) : (
							<PlainTable<InvoiceLine>columns={lineColumns} data={lines} size="sm" />
						)}

						{received.length > 0 && (
							<ITStack spacing={3}>
								<ITText as="h2" className="sf:text-title-lg">
									{t("detail.reception")}
								</ITText>
								<PlainTable<{ line: InvoiceLine; assignment: Assignment }>columns={receptionColumns} data={received} size="sm" />
							</ITStack>
						)}

						{refusal && (
							<ITAlert variant="error" dismissible onDismiss={() => setRefusal(null)}>
								{refusal}
							</ITAlert>
						)}

						{/* Closing by hand: only while the invoice is open and has something in it (F8-03, F8-07). */}
						{can.create && open && lines.length > 0 && (
							<ITFlex justify="end">
								<ITButton label={closing ? t("modal.saving") : t("closing.button")} onClick={() => void closeInvoice()} disabled={closing} />
							</ITFlex>
						)}
					</ITStack>
				</ITStack>
			)}

			<AssignDialog
				invoiceId={id}
				line={assigning}
				staff={staff}
				onClose={() => setAssigning(null)}
				onSaved={() => {
					setAssigning(null);
					toast(t("common:saved"));
					void load();
				}}
			/>
			<AdjustDialog
				assignmentId={adjusting?.assignment.id ?? null}
				summary={
					adjusting && {
						expected: quantity(adjusting.assignment.expectedQuantity, adjusting.line),
						counted: quantity(adjusting.assignment.countedQuantity, adjusting.line),
						difference: `${adjusting.assignment.difference! > 0 ? "+" : ""}${quantity(adjusting.assignment.difference, adjusting.line)}`,
					}
				}
				onClose={() => setAdjusting(null)}
				onSaved={() => {
					setAdjusting(null);
					toast(t("adjustDialog.done"));
					void load();
				}}
			/>
		</ITPage>
	);
}
