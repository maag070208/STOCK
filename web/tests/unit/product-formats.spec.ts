// unit: the product dialog mirrors the API's format rules, so the fields answer while they are written
// (products.md: the code's shape, and a barcode that is UPC-A or EAN-13 only when its check digit holds).
import { test, expect } from "@playwright/test";
import { barcodeInvalid, codeInvalid } from "../../src/entities/product/model/formats";

test("the code is 2 to 20 of uppercase letters, digits and hyphens", () => {
	expect(codeInvalid("MAT-CAL-025")).toBe(false);
	expect(codeInvalid("A")).toBe(true);
	expect(codeInvalid("MAT_CAL")).toBe(true); // underscore is not allowed
	expect(codeInvalid("MAT CAL")).toBe(true);
	expect(codeInvalid("A".repeat(21))).toBe(true);
});

test("the barcode is 6 to 20 of the same characters, and 12 or 13 digits must pass the check digit", () => {
	expect(barcodeInvalid("MAT-CAL-025")).toBe(false);
	expect(barcodeInvalid("12345")).toBe(true); // too short
	// 7501054530008 is a valid EAN-13; changing the last digit breaks it.
	expect(barcodeInvalid("7501054530008")).toBe(false);
	expect(barcodeInvalid("7501054530007")).toBe(true);
});
