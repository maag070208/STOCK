// The formats of a partner's fields, the rules of the API (validation.md section 3) mirrored so the form answers
// while the value is written. Each says whether the value is invalid; the API decides again when the form is sent.
// The taxId is the Mexican RFC: 3 letters for a company or 4 for a person, the date as YYMMDD, and 3 more characters.

const TAX_ID = /^[A-ZÑ&]{3,4}([0-9]{6})[A-Z0-9]{3}$/;
const POSTAL_CODE = /^[0-9]{5}$/;

export function taxIdInvalid(value: string): boolean {
	const match = TAX_ID.exec(value);
	if (!match) return true;
	const [year, month, day] = match[1].match(/../g)!.map((part) => Number(part));
	const real = [1900, 2000].some((century) => {
		const date = new Date(Date.UTC(century + year, month - 1, day));
		return date.getUTCFullYear() === century + year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
	});
	return !real;
}

export function postalCodeInvalid(value: string): boolean {
	return !POSTAL_CODE.test(value);
}
