import { type ColumnFilters, ITAlert, ITButton, ITCheckbox, ITDatePicker, ITFlex, ITSelect, ITText } from "@axzydev/axzy_ui_system";
import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { PartnerPicker, type PartnerKind } from "@entities/partner";
import { reportApi, type ReportFormat, type ReportKind, type ReportRef, type ReportTotal } from "@entities/report";
import { useCan } from "@entities/user";
import { isoDate } from "@shared/lib/format";
import { saveFile } from "@shared/lib/saveFile";

// The warehouses a report answered, so the filter offers nothing outside the reader's scope (reports.md rule 3). A
// required one has no "every warehouse" option: the kardex is of one material in one warehouse.
export function WarehouseFilter({ options, value, onChange, required = false }: { options: ReportRef[]; value: string; onChange: (value: string) => void; required?: boolean }) {
	const { t } = useTranslation(["reports", "common"]);
	const choices = options.map((option) => ({ value: option.id, label: `${option.code ?? ""} · ${option.name ?? ""}` }));
	return (
		<ITSelect
			name="warehouseId"
			label={t("filters.warehouse")}
			value={value}
			onChange={(event) => onChange(event.target.value)}
			options={required ? choices : [{ value: "", label: t("filters.allWarehouses") }, ...choices]}
			valueField="value"
			labelField="label"
			required={required}
			error={required && !value && choices.length > 0 ? t("common:validation.required") : undefined}
		/>
	);
}

// The supplier of the invoices report, or the customer of the orders report: the partners are searched, because an
// installation holds hundreds and the first page is not the list (reports.md section 3).
export function PartnerFilter({ kind, value, onChange }: { kind: PartnerKind; value: string; onChange: (value: string) => void }) {
	const { t } = useTranslation("reports");
	return (
		<PartnerPicker
			kind={kind}
			name={kind === "suppliers" ? "supplierId" : "customerId"}
			label={t(kind === "suppliers" ? "filters.supplier" : "filters.customer")}
			value={value}
			onChange={(partner) => onChange(partner?.id ?? "")}
		/>
	);
}

// The period of a report: two days, both inclusive. The API reads them in the business zone, so they travel as dates.
export function DateRangeFilter({ from, to, onFrom, onTo }: { from: Date | null; to: Date | null; onFrom: (value: Date | null) => void; onTo: (value: Date | null) => void }) {
	const { t } = useTranslation("reports");
	// The kit answers a single date or a range, and an input event carries text: only a date is a filter.
	const pick = (value: unknown): Date | null => {
		if (value instanceof Date) return value;
		if (Array.isArray(value)) return (value[0] as Date | null) ?? null;
		return null;
	};
	return (
		<>
			<ITDatePicker name="from" label={t("filters.from")} value={from ?? undefined} onChange={(event) => onFrom(pick(event.target?.value))} />
			<ITDatePicker name="to" label={t("filters.to")} value={to ?? undefined} onChange={(event) => onTo(pick(event.target?.value))} />
		</>
	);
}

export function BelowMinimumFilter({ value, onChange }: { value: boolean; onChange: (value: boolean) => void }) {
	const { t } = useTranslation("reports");
	return <ITCheckbox name="belowMinimum" label={t("filters.belowMinimum")} checked={value} onChange={onChange} />;
}

// The period of a filter set, as the API reads it: the day, in the business zone (reports.md section 3).
export const periodOf = (from: Date | null, to: Date | null): ColumnFilters => ({
	...(from ? { from: isoDate(from) } : {}),
	...(to ? { to: isoDate(to) } : {}),
});

// A group of totals: what the rows of the report add up to, by warehouse, by aisle and rack, by status or by type.
export function ReportTotals({ label, entries, testId }: { label: string; entries: { key: string; label: string; value: string }[]; testId?: string }) {
	return (
		<ITText as="section" aria-label={label} data-testid={testId} className="sf:mb-4 sf:flex sf:flex-wrap sf:gap-x-6 sf:gap-y-2">
			{entries.map((entry) => (
				<ITText key={entry.key} className="sf:text-body-md sf:text-on-surface">
					<ITText as="span" muted>
						{entry.label}:{" "}
					</ITText>
					{entry.value}
				</ITText>
			))}
		</ITText>
	);
}

// One group of the totals the report sent, as the strip shows it.
export const totalEntries = (totals: ReportTotal[], format: (quantity: number) => string): { key: string; label: string; value: string }[] =>
	totals.map((total) => ({ key: total.key, label: total.label, value: `${format(total.quantity)} · ${total.rows}` }));

// The file of the report on screen (reports.md section 4): the API builds it from the same filters, and reports.export
// decides whether the button is there at all.
export function ExportButtons({ kind, filters }: { kind: ReportKind; filters: ColumnFilters }) {
	const { t } = useTranslation("reports");
	const canExport = useCan("reports.export");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	if (!canExport) return null;

	async function take(format: ReportFormat) {
		setBusy(true);
		setError(null);
		try {
			const { blob, fileName } = await reportApi.exportFile(kind, format, filters);
			saveFile(blob, fileName || `reporte-${kind}.${format.toLowerCase()}`);
		} catch {
			setError(t("export.error"));
		} finally {
			setBusy(false);
		}
	}

	return (
		<ITFlex align="center" justify="end" gap={2}>
			{error && <ITAlert variant="error">{error}</ITAlert>}
			<ITButton size="sm" variant="outlined" label={t("export.xlsx")} disabled={busy} onClick={() => void take("XLSX")} />
			<ITButton size="sm" variant="outlined" label={t("export.pdf")} disabled={busy} onClick={() => void take("PDF")} />
		</ITFlex>
	);
}

// The message a report shows when the API refuses it, with the API's own words.
export function ReportError({ message }: { message: string | null }): ReactNode {
	return message ? (
		<ITAlert variant="error">
			<ITText as="span" data-testid="report-error">
				{message}
			</ITText>
		</ITAlert>
	) : null;
}
