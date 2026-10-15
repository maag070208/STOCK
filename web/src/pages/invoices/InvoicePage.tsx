import { type Column, ITAlert, ITBadget, ITButton, ITCard, ITEmptyState, ITFlex, ITGrid, ITPage, ITStack, ITStatCard, ITText } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaBalanceScale, FaTrash, FaUserEdit, FaUserPlus } from "react-icons/fa";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { toast } from "@app/store";
import { captureSummary, invoiceApi, staffApi, type Assignment, type Invoice, type InvoiceLine, type StaffMember } from "@entities/invoice";
import { useCan } from "@entities/user";
import { AdjustDialog, AssignDialog } from "@features/invoice/InvoiceDialogs";
import { QuickCapture } from "@features/invoice/QuickCapture";
import { ApiError } from "@shared/api/client";
import { IconButton } from "@shared/ui/IconButton";
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
				breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title"), onClick: () => navigate("/invoices") })}
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

	// One table for the whole invoice: a row for each share of a line (what its operator has to receive, counted and
	// placed), and one row for a line nobody has been given yet. The work happens in the row that shows it.
	type Row = { key: string; line: InvoiceLine; assignment: Assignment | null };
	const rows: Row[] = lines.flatMap((line): Row[] =>
		line.assignments.length === 0
			? [{ key: line.id, line, assignment: null }]
			: line.assignments.map((assignment): Row => ({ key: assignment.id, line, assignment })),
	);

	const columns: Column<Row>[] = [
		{ key: "product", label: t("form.product"), type: "string", render: (row) => `${row.line.product.code ?? ""} · ${row.line.product.name ?? ""}` },
		{ key: "quantity", label: t("form.quantity"), type: "string", render: (row) => `${quantity(row.line.quantity, row.line)} ${row.line.unit?.label ?? ""}` },
		{ key: "operator", label: t("form.operator"), type: "string", render: (row) => row.assignment?.operator.name ?? row.assignment?.operator.code ?? t("detail.notAssigned") },
		{ key: "expected", label: t("detail.expected"), type: "string", render: (row) => (row.assignment ? quantity(row.assignment.expectedQuantity, row.line) : "") },
		{ key: "counted", label: t("detail.counted"), type: "string", render: (row) => (row.assignment ? quantity(row.assignment.countedQuantity, row.line) : "") },
		{
			key: "outcome",
			label: t("columns.status"),
			type: "string",
			render: (row) =>
				!row.assignment || row.assignment.outcome === null ? (
					row.assignment ? <ITBadget color="gray" label={t("detail.notCounted")} /> : ""
				) : row.assignment.outcome === "MATCH" ? (
					<ITBadget color="success" label={t("detail.match")} />
				) : (
					<ITBadget color="danger" label={`${t("detail.discrepancy")} ${row.assignment.difference! > 0 ? "+" : ""}${quantity(row.assignment.difference, row.line)}`} />
				),
		},
		{ key: "placed", label: t("detail.placed"), type: "string", render: (row) => (row.assignment ? quantity(row.assignment.placedQuantity, row.line) : "") },
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (row) => (
				<ITFlex gap={2} justify="end">
					{/* A person: the material is given to one, or moved to another. */}
					{open && can.assign && row.assignment?.countedQuantity == null && (
						<IconButton
							icon={row.assignment ? <FaUserEdit aria-hidden /> : <FaUserPlus aria-hidden />}
							label={row.assignment ? t("detail.reassign") : t("detail.assign")}
							variant="outlined"
							onClick={() => setAssigning(row.line)}
						/>
					)}
					{can.resolve && row.assignment?.outcome === "DISCREPANCY" && invoice?.status.code === "PENDING" && row.assignment && (
						<IconButton icon={<FaBalanceScale aria-hidden />} label={t("detail.adjust")} onClick={() => setAdjusting({ line: row.line, assignment: row.assignment! })} />
					)}
					{open && can.create && <IconButton icon={<FaTrash aria-hidden />} label={t("common:actions.remove")} color="danger" onClick={() => void removeLine(row.line)} />}
				</ITFlex>
			),
		},
	];

	return (
		<ITPage
			title={invoice ? `${invoice.number} · ${invoice.supplier.name ?? ""}` : t("title")}
			breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title"), onClick: () => navigate("/invoices") }, { label: invoice?.number ?? t("title") })}
			loading={!invoice}
			noPadding
			// The state, and the one action that changes it, live together at the top of the page.
			actions={
				invoice && (
					<ITFlex align="center" gap={3}>
						<ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />
						{can.create && open && lines.length > 0 && (
							<ITButton label={closing ? t("modal.saving") : t("closing.button")} onClick={() => void closeInvoice()} disabled={closing} />
						)}
					</ITFlex>
				)
			}
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

						{rows.length === 0 ? (
							<ITEmptyState title={open ? t("capture.empty") : t("detail.noLines")} />
						) : (
							<PlainTable<Row> columns={columns} data={rows} size="sm" />
						)}

						{refusal && (
							<ITAlert variant="error" dismissible onDismiss={() => setRefusal(null)}>
								{refusal}
							</ITAlert>
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
