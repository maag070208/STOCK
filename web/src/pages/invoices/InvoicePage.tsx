import { ITAlert, ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
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

// One invoice: its header, its quick capture bar and its lines with their assignments, counts and placements (F8-07).
// While nothing has been counted the invoice is open: materials are captured above the table and it can be closed by
// hand; from the first count on it is only read here.
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

	const back = <ITButton variant="outlined" label={t("detail.back")} onClick={() => navigate("/invoices")} />;
	if (missing) {
		return (
			<ITPage title={t("title")} noPadding>
				<p className="sf:text-body-md">{t("common:errors.notFound")}</p>
				{back}
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

	return (
		<ITPage title={invoice ? `${invoice.number} · ${invoice.supplier.name ?? ""}` : t("title")} noPadding loading={!invoice} actions={back}>
			{invoice && (
				<div className="sf:flex sf:flex-col sf:gap-6">
					<dl className="sf:grid sf:max-w-3xl sf:grid-cols-[max-content_1fr] sf:gap-x-8 sf:gap-y-3" data-testid="invoice-header">
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("columns.status")}</dt>
						<dd>
							<ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />
						</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.supplier")}</dt>
						<dd className="sf:text-body-md">{`${invoice.supplier.code ?? ""} · ${invoice.supplier.name ?? ""}`}</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.date")}</dt>
						<dd className="sf:text-body-md" data-testid="invoice-date">
							{formatDate(invoice.invoiceDate, i18n.language)}
						</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.warehouse")}</dt>
						<dd className="sf:text-body-md">{invoice.warehouse.code}</dd>
					</dl>

					{!open && <p className="sf:text-body-md sf:text-on-surface-muted">{t("closing.readOnly")}</p>}

					{can.create && open && (
						<section aria-label={t("capture.title")} className="sf:flex sf:flex-col sf:gap-3 sf:rounded-md sf:border sf:border-outline-variant sf:p-4">
							<h2 className="sf:text-title-md">{t("capture.title")}</h2>
							<QuickCapture invoiceId={id} lines={lines} autoFocus={(location.state as { focus?: string } | null)?.focus === "capture"} onSaved={() => void load()} />
						</section>
					)}

					<section aria-label={t("detail.lines")} className="sf:flex sf:flex-col sf:gap-3" data-testid="invoice-lines">
						<div className="sf:flex sf:flex-wrap sf:items-center sf:justify-between sf:gap-2">
							<h2 className="sf:text-title-md">{t("detail.lines")}</h2>
							<p className="sf:text-body-md sf:text-on-surface-muted" data-testid="invoice-summary">
								{`${t("capture.summaryLines", { count: summary.lines })} · ${t("capture.summaryPieces", { count: summary.pieces })}`}
							</p>
						</div>

						{lines.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{open ? t("capture.empty") : t("detail.noLines")}</p>}

						{lines.map((line) => (
							<article key={line.id} className="sf:flex sf:flex-col sf:gap-2 sf:rounded-md sf:border sf:border-outline-variant sf:p-4" data-line={line.product.code ?? ""}>
								<div className="sf:flex sf:flex-wrap sf:items-center sf:justify-between sf:gap-2">
									<div className="sf:text-body-md">
										<span className="sf:font-semibold">{`${line.product.code} · ${line.product.name}`}</span>
										<span className="sf:ml-3 sf:tabular-nums">{`${quantity(line.quantity, line)} ${line.unit?.label ?? ""}`}</span>
									</div>
									{open && (
										<div className="sf:flex sf:gap-2">
											{can.assign && !line.assignments.some((assignment) => assignment.countedQuantity !== null) && (
												<ITButton size="sm" variant="outlined" label={line.assignments.length ? t("detail.reassign") : t("detail.assign")} onClick={() => setAssigning(line)} />
											)}
											{can.create && <ITButton size="sm" variant="text" label={t("common:actions.remove")} onClick={() => void removeLine(line)} />}
										</div>
									)}
								</div>
								{line.assignments.length === 0 ? (
									<p className="sf:text-body-md sf:text-on-surface-muted">{t("detail.notAssigned")}</p>
								) : (
									<table className="sf:w-full sf:text-left sf:text-body-md">
										<thead>
											<tr className="sf:text-label-md sf:text-on-surface-muted">
												<th className="sf:px-2 sf:py-1">{t("form.operator")}</th>
												<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.expected")}</th>
												<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.counted")}</th>
												<th className="sf:px-2 sf:py-1">{t("columns.status")}</th>
												<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.placed")}</th>
												<th className="sf:px-2 sf:py-1" />
											</tr>
										</thead>
										<tbody>
											{line.assignments.map((assignment) => (
												<tr key={assignment.id} className="sf:border-t sf:border-outline-variant" data-assignment={assignment.operator.name ?? ""}>
													<td className="sf:px-2 sf:py-1">{assignment.operator.name}</td>
													<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums">{quantity(assignment.expectedQuantity, line)}</td>
													<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums">{quantity(assignment.countedQuantity, line)}</td>
													<td className="sf:px-2 sf:py-1">
														{assignment.outcome === null ? (
															<ITBadget color="gray" label={t("detail.notCounted")} />
														) : assignment.outcome === "MATCH" ? (
															<ITBadget color="success" label={t("detail.match")} />
														) : (
															<ITBadget color="danger" label={`${t("detail.discrepancy")} ${assignment.difference! > 0 ? "+" : ""}${quantity(assignment.difference, line)}`} />
														)}
													</td>
													<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums">{quantity(assignment.placedQuantity, line)}</td>
													<td className="sf:px-2 sf:py-1 sf:text-right">
														{can.resolve && assignment.outcome === "DISCREPANCY" && invoice.status.code === "PENDING" && (
															<ITButton size="sm" label={t("detail.adjust")} onClick={() => setAdjusting({ line, assignment })} />
														)}
													</td>
												</tr>
											))}
										</tbody>
									</table>
								)}
							</article>
						))}

						{refusal && (
							<ITAlert variant="error" dismissible onDismiss={() => setRefusal(null)}>
								{refusal}
							</ITAlert>
						)}

						{/* Closing by hand: only while the invoice is open and has something in it (F8-03, F8-07). */}
						{can.create && open && lines.length > 0 && (
							<div className="sf:flex sf:justify-end">
								<ITButton label={closing ? t("modal.saving") : t("closing.button")} onClick={() => void closeInvoice()} disabled={closing} />
							</div>
						)}
					</section>
				</div>
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
