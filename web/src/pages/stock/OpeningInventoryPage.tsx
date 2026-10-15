import { ITAlert, ITBadget, ITButton, ITFlex, ITGrid, ITPage, ITStack, ITStatCard, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { stockApi, type OpeningReport } from "@entities/stock";
import { warehouseApi, WarehousePicker } from "@entities/warehouse";
import { formatNumber } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";

const none = (value: unknown) => value === null || value === undefined;

// The opening inventory (stock.md section 3): the legacy file's stock into the warehouse's temporary location.
// The file is reviewed first and nothing is written; the same file is then confirmed, once per warehouse. The screen is
// built with the components of the UI kit only (web.md section 8), with one exception: the file picker (see below).
export function OpeningInventoryPage() {
	const { t, i18n } = useTranslation(["stock", "common"]);
	const navigate = useNavigate();
	const picker = useRef<HTMLInputElement>(null);
	const [warehouseId, setWarehouseId] = useState("");
	const [file, setFile] = useState<File | null>(null);
	const [report, setReport] = useState<OpeningReport | null>(null);
	const review = useSubmit();
	const confirm = useSubmit();
	const number = (value: number | null) => (none(value) ? "" : formatNumber(Number(value), i18n.language));

	useEffect(() => {
		// CENTRAL holds the opening inventory (products.md section 5), when it exists.
		void warehouseApi.query({ page: 1, limit: 1, filters: { active: "true", code: "CENTRAL" } }).then((page) => {
			const central = page.data[0];
			if (central) setWarehouseId((current) => current || central.id);
		});
	}, []);

	const reset = () => {
		setReport(null);
		review.reset();
		confirm.reset();
	};

	async function runReview() {
		if (!file || !warehouseId) return;
		reset();
		const result = await review.run(() => stockApi.openingDryRun(file, warehouseId));
		if (result) setReport(result);
	}

	async function runConfirm() {
		if (!file || !report) return;
		const result = await confirm.run(() => stockApi.openingConfirm(file, warehouseId, report.checksum));
		if (result) setReport(result);
	}

	const control = !report ? null : none(report.controlMatches) ? "none" : report.controlMatches ? "matches" : "mismatch";
	const confirmed = report && !none(report.movements);

	return (
		<ITPage
			title={t("opening.title")}
			description={t("opening.description")}
			breadcrumbs={[{ label: t("common:nav.stock"), onClick: () => navigate("/stock") }, { label: t("opening.title") }]}
			noPadding
		>
			<ITStack spacing={6} className="sf:max-w-4xl">
				<ITGrid container spacing={3}>
					<ITGrid item xs={12} sm={6}>
						<WarehousePicker
							name="warehouseId"
							label={t("opening.warehouse")}
							value={warehouseId}
							onChange={(chosen) => {
								setWarehouseId(chosen);
								reset();
							}}
						/>
					</ITGrid>
					<ITGrid item xs={12} sm={6}>
						<ITStack spacing={1}>
							<ITText as="label" htmlFor="opening-file" className="sf:text-label-md">
								{t("opening.file")}
							</ITText>
							<ITFlex wrap="wrap" align="center" gap={3}>
								{/* The kit has no file picker: its ITDropfile takes a drop (no input to choose a file) and carries its
								    own untranslated labels, so the one control the browser gives for this stays here, hidden behind
								    the kit's button. */}
								<input
									ref={picker}
									id="opening-file"
									type="file"
									name="file"
									accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
									className="sf:sr-only"
									onChange={(e) => {
										setFile(e.target.files?.[0] ?? null);
										reset();
									}}
								/>
								<ITButton variant="outlined" label={t("opening.choose")} onClick={() => picker.current?.click()} />
								<ITText muted>{file?.name ?? t("opening.noFile")}</ITText>
							</ITFlex>
						</ITStack>
					</ITGrid>
				</ITGrid>
				<ITStack>
					<ITButton label={review.submitting ? t("opening.reviewing") : t("opening.review")} onClick={() => void runReview()} disabled={!file || !warehouseId || review.submitting} />
				</ITStack>
				{review.error && <ITAlert variant="error">{review.error}</ITAlert>}

				{report && (
					<ITText as="section" aria-label={t("opening.report")} data-testid="opening-report" className="sf:flex sf:flex-col sf:gap-4">
						<ITText as="h2" className="sf:text-title-md">
							{t("opening.report")}
						</ITText>
						<ITGrid container spacing={3}>
							<ITGrid item xs={12} md={4}>
									<ITStatCard label={t("opening.products")} value={number(report.products)} />
							</ITGrid>
							<ITGrid item xs={12} md={4}>
									<ITStatCard label={t("opening.withStock")} value={number(report.withStock)} />
							</ITGrid>
							<ITGrid item xs={12} md={4}>
									<ITStatCard label={t("opening.total")} value={number(report.total)} />
							</ITGrid>
						</ITGrid>
						<ITFlex align="center" gap={3}>
							<ITText as="span" className="sf:text-label-md">
								{t("opening.control.label")}
							</ITText>
							<ITBadget
								color={control === "mismatch" ? "danger" : control === "matches" ? "success" : "gray"}
								label={t(`opening.control.${control ?? "none"}`, { total: number(report.total), control: number(report.controlTotal) })}
							/>
						</ITFlex>
						{report.missing.length > 0 && (
							<ITAlert variant="warning">
								{t("opening.missing")} {report.missing.map((m) => `${t("opening.row")} ${m.row}: ${m.code}`).join(" · ")}
							</ITAlert>
						)}
						{report.alreadyLoaded && !confirmed && <ITAlert variant="info">{t("opening.alreadyLoaded")}</ITAlert>}
						{confirmed ? (
							<ITAlert variant="success">{report.movements! > 0 ? t("opening.done", { count: report.movements! }) : t("opening.nothing")}</ITAlert>
						) : (
							<ITFlex justify="end">
								<ITButton
									label={confirm.submitting ? t("opening.confirming") : t("opening.confirm", { count: report.withStock })}
									onClick={() => void runConfirm()}
									disabled={report.controlMatches === false || confirm.submitting}
								/>
							</ITFlex>
						)}
						{confirm.error && <ITAlert variant="error">{confirm.error}</ITAlert>}
					</ITText>
				)}
			</ITStack>
		</ITPage>
	);
}
