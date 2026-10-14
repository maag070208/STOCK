// F2-12 · unit: the import screen shows the dry run's counts before the confirm button is enabled.
import { test, expect } from "@playwright/test";
import { canConfirm, importSummary } from "../../src/entities/product/model/import";
import type { ImportReport } from "../../src/entities/product/model/types";

const report = (changes: Partial<ImportReport> = {}): ImportReport => ({
	checksum: "a".repeat(64),
	accepted: 1398,
	newProducts: 1398,
	existingProducts: 0,
	rejected: [
		{ row: 1400, field: "code", code: "FIELD_REQUIRED", message: "Este campo es obligatorio." },
		{ row: 1401, field: "code", code: "FIELD_REQUIRED", message: "Este campo es obligatorio." },
	],
	stockTotal: 10914130,
	controlTotal: 10914130,
	controlMatches: true,
	created: null,
	...changes,
});

test("without a dry run there is nothing to confirm", () => {
	expect(canConfirm(null)).toBe(false);
	expect(importSummary(null)).toBeNull();
});

test("after the dry run its counts are shown, and the confirm is enabled", () => {
	expect(importSummary(report())).toEqual({ accepted: 1398, newProducts: 1398, existingProducts: 0, rejected: 2, control: "matches" });
	expect(canConfirm(report())).toBe(true);
});

test("a file whose stock does not add up to its control total cannot be confirmed", () => {
	expect(importSummary(report({ controlMatches: false }))?.control).toBe("mismatch");
	expect(canConfirm(report({ controlMatches: false }))).toBe(false);
});

test("a file without a control total can be confirmed, and says it has none", () => {
	expect(importSummary(report({ controlTotal: null, controlMatches: null }))?.control).toBe("none");
	expect(canConfirm(report({ controlTotal: null, controlMatches: null }))).toBe(true);
});

test("a file with no accepted rows has nothing to import", () => {
	expect(canConfirm(report({ accepted: 0, newProducts: 0 }))).toBe(false);
});

test("once confirmed, the same report is not confirmed again", () => {
	expect(canConfirm(report({ created: 1398 }))).toBe(false);
});
