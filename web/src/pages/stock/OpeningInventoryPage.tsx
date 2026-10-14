import { ITAlert, ITBadget, ITButton, ITPage, ITSelect, ITStatCard } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { stockApi, type OpeningReport } from "@entities/stock";
import { warehouseApi, type Warehouse } from "@entities/warehouse";
import { formatNumber } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";

const none = (value: unknown) => value === null || value === undefined;

// The opening inventory (stock.md section 3): the legacy file's stock into the warehouse's temporary location.
// The file is reviewed first and nothing is written; the same file is then confirmed, once per warehouse.
export function OpeningInventoryPage() {
	const { t, i18n } = useTranslation(["stock", "common"]);
	const navigate = useNavigate();
	const picker = useRef<HTMLInputElement>(null);
	const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
	const [warehouseId, setWarehouseId] = useState("");
	const [file, setFile] = useState<File | null>(null);
	const [report, setReport] = useState<OpeningReport | null>(null);
	const review = useSubmit();
	const confirm = useSubmit();
	const number = (value: number | null) => (none(value) ? "" : formatNumber(Number(value), i18n.language));

	useEffect(() => {
		void warehouseApi.query({ page: 1, limit: 200, filters: { active: "true" } }).then((page) => {
			setWarehouses(page.data);
			// CENTRAL holds the opening inventory (products.md section 5), when it exists.
			setWarehouseId((current) => current || (page.data.find((w) => w.code === "CENTRAL") ?? page.data[0])?.id || "");
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
		<ITPage title={t("opening.title")} description={t("opening.description")} noPadding actions={<ITButton variant="outlined" label={t("common:actions.close")} onClick={() => navigate("/stock")} />}>
			<div className="sf:flex sf:max-w-4xl sf:flex-col sf:gap-6">
				<div className="sf:grid sf:grid-cols-1 sf:items-end sf:gap-3 sf:md:grid-cols-2">
					<ITSelect
						name="warehouseId"
						label={t("opening.warehouse")}
						value={warehouseId}
						onChange={(e) => {
							setWarehouseId(e.target.value);
							reset();
						}}
						options={warehouses.map((w) => ({ value: w.id, label: `${w.code} · ${w.name}` }))}
						valueField="value"
						labelField="label"
					/>
					<div className="sf:flex sf:flex-col sf:gap-1">
						<span className="sf:text-label-md" id="opening-file-label">{t("opening.file")}</span>
						<div className="sf:flex sf:flex-wrap sf:items-center sf:gap-3">
							<input
								ref={picker}
								type="file"
								name="file"
								aria-labelledby="opening-file-label"
								accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
								className="sf:sr-only"
								onChange={(e) => {
									setFile(e.target.files?.[0] ?? null);
									reset();
								}}
							/>
							<ITButton variant="outlined" label={t("opening.choose")} onClick={() => picker.current?.click()} />
							<span className="sf:text-body-md sf:text-on-surface-muted">{file?.name ?? t("opening.noFile")}</span>
						</div>
					</div>
				</div>
				<div>
					<ITButton label={review.submitting ? t("opening.reviewing") : t("opening.review")} onClick={() => void runReview()} disabled={!file || !warehouseId || review.submitting} />
				</div>
				{review.error && <ITAlert variant="error">{review.error}</ITAlert>}

				{report && (
					<section aria-label={t("opening.report")} className="sf:flex sf:flex-col sf:gap-4" data-testid="opening-report">
						<h2 className="sf:text-title-md">{t("opening.report")}</h2>
						<div className="sf:grid sf:grid-cols-1 sf:gap-3 sf:md:grid-cols-3">
							<ITStatCard label={t("opening.products")} value={number(report.products)} />
							<ITStatCard label={t("opening.withStock")} value={number(report.withStock)} />
							<ITStatCard label={t("opening.total")} value={number(report.total)} />
						</div>
						<div className="sf:flex sf:items-center sf:gap-3">
							<span className="sf:text-label-md">{t("opening.control.label")}</span>
							<ITBadget
								color={control === "mismatch" ? "danger" : control === "matches" ? "success" : "gray"}
								label={t(`opening.control.${control ?? "none"}`, { total: number(report.total), control: number(report.controlTotal) })}
							/>
						</div>
						{report.missing.length > 0 && (
							<ITAlert variant="warning">
								{t("opening.missing")} {report.missing.map((m) => `${t("opening.row")} ${m.row}: ${m.code}`).join(" · ")}
							</ITAlert>
						)}
						{report.alreadyLoaded && !confirmed && <ITAlert variant="info">{t("opening.alreadyLoaded")}</ITAlert>}
						{confirmed ? (
							<ITAlert variant="success">{report.movements! > 0 ? t("opening.done", { count: report.movements! }) : t("opening.nothing")}</ITAlert>
						) : (
							<div className="sf:flex sf:justify-end">
								<ITButton
									label={confirm.submitting ? t("opening.confirming") : t("opening.confirm", { count: report.withStock })}
									onClick={() => void runConfirm()}
									disabled={report.controlMatches === false || confirm.submitting}
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
