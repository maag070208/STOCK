// The shares of a line (material-entry.md section 6), checked as the API checks them, so the form blocks a wrong split
// before it is sent.
export interface ShareInput {
	operatorId: string;
	quantity: string;
}

export type ShareError = "FIELD_REQUIRED" | "QUANTITY_INVALID" | "DUPLICATE_VALUE" | "ASSIGNMENT_TOTAL_MISMATCH";

const amount = (value: string) => (value.trim() === "" ? NaN : Number(value));

export const sharesTotal = (shares: ShareInput[]): number => shares.reduce((sum, s) => sum + (Number.isFinite(amount(s.quantity)) ? amount(s.quantity) : 0), 0);

export function sharesError(line: string, shares: ShareInput[]): ShareError | null {
	if (shares.length === 0 || shares.some((s) => !s.operatorId)) return "FIELD_REQUIRED";
	if (shares.some((s) => !(amount(s.quantity) > 0))) return "QUANTITY_INVALID";
	if (new Set(shares.map((s) => s.operatorId)).size !== shares.length) return "DUPLICATE_VALUE";
	// Compared in thousandths, the finest decimals a unit allows here, so 0.1 + 0.2 still adds up to 0.3.
	return Math.round(sharesTotal(shares) * 1000) === Math.round(amount(line) * 1000) ? null : "ASSIGNMENT_TOTAL_MISMATCH";
}

// An adjustment is sent with a note that explains it (material-receiving.md section 5).
export const canAdjust = (note: string): boolean => note.trim().length > 0 && note.trim().length <= 500;
