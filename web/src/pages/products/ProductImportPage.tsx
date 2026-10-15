import { ITAlert, ITBadget, ITButton, ITFlex, ITGrid, ITPage, ITStack, ITStatCard, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { canConfirm, importSummary, productApi, type ImportReport } from "@entities/product";
import { formatNumber } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";
import { PlainTable } from "@shared/ui/PlainTable";

// One rejected row of the file, which the kit tells apart by its row and field.
type RejectedRow = Record<string, unknown> & { id: string; row: number; field: string; message: string };

// The import of the legacy inventory file (products.md section 6): the file is reviewed first and nothing is written;
// the report shows what would be imported and what not, and only then can the same file be confirmed. Everything but
// the file input — the kit has no component that keeps this flow of choosing a file — is a component of the kit.
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

	const rejected: RejectedRow[] = (report?.rejected ?? []).map((row) => ({ id: `${row.row}-${row.field}`, row: row.row, field: row.field, message: row.message }));

	const columns: Column<RejectedRow>[] = [
		{ key: "row", label: t("importPage.row"), type: "string", render: (row) => String(row.row) },
		{ key: "field", label: t("importPage.field"), type: "string", render: (row) => t(`importPage.fields.${row.field}` as "importPage.fields.code", { defaultValue: row.field }) },
		{ key: "message", label: t("importPage.reason"), type: "string", render: (row) => row.message },
	];

	return (
		<ITPage
			title={t("importPage.title")}
			description={t("importPage.description")}
			breadcrumbs={[{ label: t("title"), onClick: () => navigate("/products") }, { label: t("importPage.title") }]}
			noPadding
		>
			<ITStack spacing={6} className="sf:max-w-4xl">
				<ITStack spacing={2}>
					<ITText as="label" htmlFor="import-file" className="sf:text-label-md">
						{t("importPage.file")}
					</ITText>
					<ITFlex wrap="wrap" align="center" gap={3}>
						{/* The browser's own file button speaks the browser's language: it is hidden, and this one opens it. */}
						<input
							ref={picker}
							id="import-file"
							type="file"
							name="file"
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
						<ITText muted data-testid="import-file-name">
							{file?.name ?? t("importPage.noFile")}
						</ITText>
						<ITButton label={review.submitting ? t("importPage.reviewing") : t("importPage.review")} onClick={() => void runReview()} disabled={!file || review.submitting} />
					</ITFlex>
				</ITStack>
				{review.error && <ITAlert variant="error">{review.error}</ITAlert>}

				{report && summary && (
					<ITStack spacing={4}>
						<ITText as="h2" className="sf:text-title-md">
							{t("importPage.report")}
						</ITText>
						<ITGrid container spacing={3}>
							<ITGrid item xs={12} sm={6} md={3}>
								<ITStatCard label={t("importPage.accepted")} value={number(summary.accepted)} />
							</ITGrid>
							<ITGrid item xs={12} sm={6} md={3}>
								<ITStatCard label={t("importPage.newProducts")} value={number(summary.newProducts)} />
							</ITGrid>
							<ITGrid item xs={12} sm={6} md={3}>
								<ITStatCard label={t("importPage.existingProducts")} value={number(summary.existingProducts)} />
							</ITGrid>
							<ITGrid item xs={12} sm={6} md={3}>
								<ITStatCard label={t("importPage.rejected")} value={number(summary.rejected)} />
							</ITGrid>
						</ITGrid>
						<ITFlex align="center" gap={3}>
							<ITText className="sf:text-label-md">{t("importPage.control.label")}</ITText>
							<ITBadget
								color={summary.control === "mismatch" ? "danger" : summary.control === "matches" ? "success" : "gray"}
								label={t(`importPage.control.${summary.control}`, { stock: number(report.stockTotal), control: number(report.controlTotal) })}
							/>
						</ITFlex>

						{report.rejected.length > 0 && (
							<ITStack spacing={2}>
								<ITText as="h3" className="sf:text-title-sm">
									{t("importPage.rejectedTitle")}
								</ITText>
								<PlainTable<RejectedRow>
									columns={columns}
									data={rejected}
									size="sm"
								/>
							</ITStack>
						)}

						{report.created !== null && report.created !== undefined ? (
							<ITAlert variant="success">
								{report.created > 0 ? t("importPage.done", { created: number(report.created), existing: number(report.existingProducts) }) : t("importPage.nothingNew")}
							</ITAlert>
						) : (
							<ITFlex justify="end">
								<ITButton
									label={confirm.submitting ? t("importPage.confirming") : t("importPage.confirm", { count: report.newProducts })}
									onClick={() => void runConfirm()}
									disabled={!canConfirm(report) || confirm.submitting}
								/>
							</ITFlex>
						)}
						{confirm.error && <ITAlert variant="error">{confirm.error}</ITAlert>}
					</ITStack>
				)}
			</ITStack>
		</ITPage>
	);
}
