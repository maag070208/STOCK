// The rack form of locations.md section 2, with the API's rules (validation.md, location fields).
// The form shows the codes before it is sent; the API decides.
export interface RackValues {
	aisle: string;
	rack: string;
	levels: string;
}

export type RackErrorCode = "FIELD_REQUIRED" | "FORMAT_INVALID";

const AISLE = /^[A-Z]$/;
const RACK = /^[0-9]{2}$/;
export const MAX_LEVELS = 26;

const levelCount = (value: string): number | null => (/^[0-9]+$/.test(value.trim()) ? Number(value.trim()) : null);

export function rackErrors(values: RackValues): Partial<Record<keyof RackValues, RackErrorCode>> {
	const errors: Partial<Record<keyof RackValues, RackErrorCode>> = {};
	const aisle = values.aisle.trim().toUpperCase();
	const rack = values.rack.trim();
	const levels = levelCount(values.levels);
	if (!aisle) errors.aisle = "FIELD_REQUIRED";
	else if (!AISLE.test(aisle)) errors.aisle = "FORMAT_INVALID";
	if (!rack) errors.rack = "FIELD_REQUIRED";
	else if (!RACK.test(rack)) errors.rack = "FORMAT_INVALID";
	if (!values.levels.trim()) errors.levels = "FIELD_REQUIRED";
	else if (levels === null || levels < 1 || levels > MAX_LEVELS) errors.levels = "FORMAT_INVALID";
	return errors;
}

// One code per level, lettered from A: K05A, K05B, K05C. Empty while any part is not valid yet.
export function rackCodes(values: RackValues): string[] {
	if (Object.keys(rackErrors(values)).length > 0) return [];
	const prefix = values.aisle.trim().toUpperCase() + values.rack.trim();
	return Array.from({ length: levelCount(values.levels)! }, (_, index) => prefix + String.fromCharCode(65 + index));
}

// The API answers a rack whose codes exist with one detail per code, as the field `codes.<code>`.
export const existingCodes = (fieldErrors: Record<string, string>): string[] =>
	Object.keys(fieldErrors)
		.filter((field) => field.startsWith("codes."))
		.map((field) => field.slice("codes.".length))
		.sort();
