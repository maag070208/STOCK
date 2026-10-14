import { ITAlert, ITBadget, ITButton, ITPage, ITStatCard } from "@axzydev/axzy_ui_system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { canConfirm, importSummary, productApi, type ImportReport } from "@entities/product";
import { formatNumber } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";

// The import of the legacy inventory file (products.md section 6): the file is reviewed first and nothing is written;
// the report shows what would be imported and what not, and only then can the same file be confirmed.
export function ProductImportPage() {
	const { t, i18n } = useTranslation(["products", "common"]);
	const navigate = useNavigate();
	const picker = useRef<HTMLInputElement>(null);
	const [file, setFile] = useState<File | null>(null);
	const [report, setReport] = useState<ImportReport | null>(null);
	const review = useSubmit();
	const confirm = useSubmit();
	const summary = importSummary(report);
	const number = (value: number | null) => (value === null ? "" : formatNumber(value, i18n.language));

	async function runReview() {
		if (!file) return;
		setReport(null);
		confirm.reset();
		const result = await review.run(() => productApi.importDryRun(file));
		if (result) setReport(result);
	}

	async function runConfirm() {
		if (!file || !report) return;
		const result = await confirm.run(() => productApi.importConfirm(file, report.checksum));
		if (result) setReport(result);
	}

	return (
		<ITPage
			title={t("importPage.title")}
			description={t("importPage.description")}
			noPadding
			actions={<ITButton variant="outlined" label={t("detail.back")} onClick={() => navigate("/products")} />}
		>
			<div className="sf:flex sf:max-w-4xl sf:flex-col sf:gap-6">
				<div className="sf:flex sf:flex-col sf:gap-2">
					<span className="sf:text-label-md" id="import-file-label">{t("importPage.file")}</span>
					<div className="sf:flex sf:flex-wrap sf:items-center sf:gap-3">
						{/* The browser's own file button speaks the browser's language: it is hidden, and this one opens it. */}
						<input
							ref={picker}
							type="file"
							name="file"
							aria-labelledby="import-file-label"
							accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
							className="sf:sr-only"
							onChange={(e) => {
								setFile(e.target.files?.[0] ?? null);
								setReport(null);
								review.reset();
								confirm.reset();
							}}
						/>
						<ITButton variant="outlined" label={t("importPage.choose")} onClick={() => picker.current?.click()} />
						<span className="sf:text-body-md sf:text-on-surface-muted" data-testid="import-file-name">{file?.name ?? t("importPage.noFile")}</span>
						<ITButton label={review.submitting ? t("importPage.reviewing") : t("importPage.review")} onClick={() => void runReview()} disabled={!file || review.submitting} />
					</div>
				</div>
				{review.error && <ITAlert variant="error">{review.error}</ITAlert>}

				{report && summary && (
					<section aria-label={t("importPage.report")} className="sf:flex sf:flex-col sf:gap-4" data-testid="import-report">
						<h2 className="sf:text-title-md">{t("importPage.report")}</h2>
						<div className="sf:grid sf:grid-cols-2 sf:gap-3 sf:md:grid-cols-4">
							<ITStatCard label={t("importPage.accepted")} value={number(summary.accepted)} />
							<ITStatCard label={t("importPage.newProducts")} value={number(summary.newProducts)} />
							<ITStatCard label={t("importPage.existingProducts")} value={number(summary.existingProducts)} />
							<ITStatCard label={t("importPage.rejected")} value={number(summary.rejected)} />
						</div>
						<div className="sf:flex sf:items-center sf:gap-3">
							<span className="sf:text-label-md">{t("importPage.control.label")}</span>
							<ITBadget
								color={summary.control === "mismatch" ? "danger" : summary.control === "matches" ? "success" : "gray"}
								label={t(`importPage.control.${summary.control}`, { stock: number(report.stockTotal), control: number(report.controlTotal) })}
							/>
						</div>

						{report.rejected.length > 0 && (
							<div className="sf:flex sf:flex-col sf:gap-2">
								<h3 className="sf:text-title-sm">{t("importPage.rejectedTitle")}</h3>
								<table className="sf:w-full sf:text-left sf:text-body-md" data-testid="rejected-rows">
									<thead>
										<tr className="sf:text-label-md sf:text-on-surface-muted">
											<th className="sf:py-2">{t("importPage.row")}</th>
											<th className="sf:py-2">{t("importPage.field")}</th>
											<th className="sf:py-2">{t("importPage.reason")}</th>
										</tr>
									</thead>
									<tbody>
										{report.rejected.map((row) => (
											<tr key={`${row.row}-${row.field}`} className="sf:border-t sf:border-outline-variant">
												<td className="sf:py-2">{row.row}</td>
												<td className="sf:py-2">{t(`importPage.fields.${row.field}` as "importPage.fields.code", { defaultValue: row.field })}</td>
												<td className="sf:py-2">{row.message}</td>
											</tr>
										))}
									</tbody>
								</table>
							</div>
						)}

						{report.created !== null && report.created !== undefined ? (
							<ITAlert variant="success">
								{report.created > 0 ? t("importPage.done", { created: number(report.created), existing: number(report.existingProducts) }) : t("importPage.nothingNew")}
							</ITAlert>
						) : (
							<div className="sf:flex sf:justify-end">
								<ITButton
									label={confirm.submitting ? t("importPage.confirming") : t("importPage.confirm", { count: report.newProducts })}
									onClick={() => void runConfirm()}
									disabled={!canConfirm(report) || confirm.submitting}
								/>
							</div>
						)}
						{confirm.error && <ITAlert variant="error">{confirm.error}</ITAlert>}
					</section>
				)}
			</div>
		</ITPage>
	);
}
