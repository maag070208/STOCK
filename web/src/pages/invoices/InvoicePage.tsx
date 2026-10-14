import { ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { invoiceApi, staffApi, type Assignment, type Invoice, type InvoiceLine, type StaffMember } from "@entities/invoice";
import { useCan } from "@entities/user";
import { AddLineDialog, AdjustDialog, AssignDialog } from "@features/invoice/InvoiceDialogs";
import { formatDate, formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";
import { STATUS_COLOR } from "./InvoicesPage";

// One invoice: its header, its status, and each line with its assignments, counts and placements. Lines are added and
// assigned here (path A) while the invoice is open or pending; discrepancies are adjusted here too.
export function InvoicePage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const { id = "" } = useParams();
	const navigate = useNavigate();
	const can = { create: useCan("invoices.create"), assign: useCan("receipts.assign"), resolve: useCan("discrepancies.resolve") };
	const [invoice, setInvoice] = useState<Invoice | null>(null);
	const [missing, setMissing] = useState(false);
	const [staff, setStaff] = useState<StaffMember[]>([]);
	const [assigning, setAssigning] = useState<InvoiceLine | null>(null);
	const [adding, setAdding] = useState(false);
	const [adjusting, setAdjusting] = useState<{ line: InvoiceLine; assignment: Assignment } | null>(null);

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

	const editable = invoice?.status.code === "OPEN" || invoice?.status.code === "PENDING";
	const quantity = (value: number | null | undefined, line: InvoiceLine) => (value === null || value === undefined ? "" : formatQuantity(value, line.unit?.decimalPlaces, i18n.language));

	return (
		<ITPage
			title={invoice ? `${invoice.number} · ${invoice.supplier.name ?? ""}` : t("title")}
			noPadding
			loading={!invoice}
			actions={
				<div className="sf:flex sf:gap-2">
					{back}
					{can.create && editable && <ITButton label={t("detail.addLine")} onClick={() => setAdding(true)} />}
				</div>
			}
		>
			{invoice && (
				<div className="sf:flex sf:flex-col sf:gap-6">
					<dl className="sf:grid sf:max-w-3xl sf:grid-cols-[max-content_1fr] sf:gap-x-8 sf:gap-y-3" data-testid="invoice-header">
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("columns.status")}</dt>
						<dd>
							<ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />
						</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.supplier")}</dt>
						<dd className="sf:text-body-md">{`${invoice.supplier.code ?? ""} · ${invoice.supplier.name ?? ""}`}</dd>
						{invoice.contact && (
							<>
								<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.contact")}</dt>
								<dd className="sf:text-body-md">{invoice.contact.name}</dd>
							</>
						)}
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.date")}</dt>
						<dd className="sf:text-body-md">{formatDate(invoice.invoiceDate, i18n.language)}</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.warehouse")}</dt>
						<dd className="sf:text-body-md">{invoice.warehouse.code}</dd>
					</dl>

					<section aria-label={t("detail.lines")} className="sf:flex sf:flex-col sf:gap-3" data-testid="invoice-lines">
						<h2 className="sf:text-title-md">{t("detail.lines")}</h2>
						{invoice.lines?.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("detail.noLines")}</p>}
						{invoice.lines?.map((line) => (
							<article key={line.id} className="sf:flex sf:flex-col sf:gap-2 sf:rounded-md sf:border sf:border-outline-variant sf:p-4" data-line={line.product.code ?? ""}>
								<div className="sf:flex sf:flex-wrap sf:items-center sf:justify-between sf:gap-2">
									<div className="sf:text-body-md">
										<span className="sf:font-semibold">{`${line.product.code} · ${line.product.name}`}</span>
										<span className="sf:ml-3 sf:tabular-nums">{`${quantity(line.quantity, line)} ${line.unit?.label ?? ""}`}</span>
									</div>
									{can.assign && editable && !line.assignments.some((a) => a.countedQuantity !== null) && (
										<ITButton size="sm" variant="outlined" label={line.assignments.length ? t("detail.reassign") : t("detail.assign")} onClick={() => setAssigning(line)} />
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
			<AddLineDialog
				invoiceId={id}
				isOpen={adding}
				onClose={() => setAdding(false)}
				onSaved={() => {
					setAdding(false);
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
