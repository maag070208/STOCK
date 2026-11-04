// unit: the partner form mirrors the API's format rules, so the fields answer while they are written
// (validation.md section 3: the Mexican RFC and the 5-digit postal code).
import { test, expect } from "@playwright/test";
import { postalCodeInvalid, taxIdInvalid } from "../../src/entities/partner/model/formats";

test("the RFC is 3-4 letters, a real date as YYMMDD, and 3 more characters", () => {
	expect(taxIdInvalid("VECA880517")).toBe(true); // incomplete
	expect(taxIdInvalid("VEC880517XYZ1")).toBe(true); // too long
	expect(taxIdInvalid("VEC999517XYZ")).toBe(true); // month 95 does not exist
	expect(taxIdInvalid("VEC880230XYZ")).toBe(true); // February 30th does not exist
	expect(taxIdInvalid("VEC880517XYZ")).toBe(false); // company
	expect(taxIdInvalid("VECA880517MDF")).toBe(false); // person
});

test("the postal code is 5 digits", () => {
	expect(postalCodeInvalid("44100")).toBe(false);
	expect(postalCodeInvalid("4410")).toBe(true);
	expect(postalCodeInvalid("44100-1")).toBe(true);
	expect(postalCodeInvalid("44a00")).toBe(true);
});
