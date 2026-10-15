// Dates for people: the local day and time, in the language of the interface.
export function formatDateTime(value: string | null | undefined, locale: string): string {
	if (!value) return "";
	return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

// Numbers follow Mexico's convention in Spanish (1,234.50), where the business is, and the language's own otherwise.
const numberLocale = (locale: string) => (locale === "es" ? "es-MX" : locale);

// Whole quantities and counts, with the thousands separator of the interface's language.
export function formatNumber(value: number, locale: string): string {
	return new Intl.NumberFormat(numberLocale(locale)).format(value);
}

// A quantity with exactly the decimals its unit allows (the unit's decimal_places), with the language's separators.
// The API sends quantities as numbers or as decimal text.
export function formatQuantity(value: number | string, decimals: number | null | undefined, locale: string): string {
	const places = decimals ?? 0;
	return new Intl.NumberFormat(numberLocale(locale), { minimumFractionDigits: places, maximumFractionDigits: places }).format(Number(value));
}

// A calendar date (YYYY-MM-DD from the API) as the interface's language writes it. It has no time, so no time zone moves it.
export function formatDate(value: string | null | undefined, locale: string): string {
	if (!value) return "";
	const [year, month, day] = value.split("-").map(Number);
	return new Intl.DateTimeFormat(numberLocale(locale), { dateStyle: "medium" }).format(new Date(year, month - 1, day));
}

// The business day of the API (America/Mexico_City). The due date of an order is checked against it, and the browser's
// own calendar can be a day behind (or ahead) of it, so a screen that needs the business day takes this one.
const BUSINESS_ZONE = "America/Mexico_City";

export function businessToday(): Date {
	const [year, month, day] = new Intl.DateTimeFormat("en-CA", { timeZone: BUSINESS_ZONE, year: "numeric", month: "2-digit", day: "2-digit" })
		.format(new Date())
		.split("-")
		.map(Number);
	return new Date(year, month - 1, day);
}

// A day as the API takes it: YYYY-MM-DD in the person's own calendar.
export function isoDate(date: Date): string {
	return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
