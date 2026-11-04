// The formats of a product's fields, the rules of the API (products.md) mirrored so the dialog answers while the
// value is written; the API decides again when the form is sent.

const CODE = /^[A-Z0-9-]{2,20}$/;
const BARCODE = /^[A-Z0-9-]{6,20}$/;

export function codeInvalid(value: string): boolean {
	return !CODE.test(value);
}

export function barcodeInvalid(value: string): boolean {
	if (!BARCODE.test(value)) return true;
	// A 12 or 13 digit barcode is UPC-A or EAN-13: its check digit has to hold (barcode-scanning.md).
	if ((value.length === 12 || value.length === 13) && /^\d+$/.test(value)) return !checkDigitHolds(value);
	return false;
}

// EAN-13 and UPC-A share the rule: from the right of the body, weights 3 and 1 alternate.
function checkDigitHolds(digits: string): boolean {
	const sum = digits
		.slice(0, -1)
		.split("")
		.reverse()
		.map((digit, index) => Number(digit) * (index % 2 === 0 ? 3 : 1))
		.reduce((total, part) => total + part, 0);
	return (10 - (sum % 10)) % 10 === Number(digits.at(-1));
}
