import type { ImportReport } from "./types";

export type ControlResult = "matches" | "mismatch" | "none";

export interface ImportSummary {
	accepted: number;
	newProducts: number;
	existingProducts: number;
	rejected: number;
	control: ControlResult;
}

// The API leaves out a value it does not have, or sends it as null: both mean "none".
const none = (value: unknown) => value === null || value === undefined;

// What the dry run found, as the screen shows it before anything is written.
export function importSummary(report: ImportReport | null): ImportSummary | null {
	if (!report) return null;
	return {
		accepted: report.accepted,
		newProducts: report.newProducts,
		existingProducts: report.existingProducts,
		rejected: report.rejected.length,
		control: none(report.controlMatches) ? "none" : report.controlMatches ? "matches" : "mismatch",
	};
}

// The confirm opens only after a dry run that has rows to import, whose stock adds up to its control total,
// and that was not confirmed yet. The API checks the same again.
export const canConfirm = (report: ImportReport | null): boolean =>
	!!report && none(report.created) && report.accepted > 0 && report.controlMatches !== false;
