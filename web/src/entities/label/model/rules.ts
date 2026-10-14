// The checks the forms make before asking the API, which checks again (validation.md, labels.md sections 5 and 6).

export const MIN_COPIES = 1;
export const MAX_COPIES = 100;

// The copies of a print: a whole number from 1 to 100.
export function copiesError(value: string): "required" | "copiesRange" | undefined {
	const text = value.trim();
	if (!text) return "required";
	if (!/^\d+$/.test(text)) return "copiesRange";
	const copies = Number(text);
	return copies >= MIN_COPIES && copies <= MAX_COPIES ? undefined : "copiesRange";
}

// Four octets from 0 to 255, without leading zeros.
export function isIpAddress(value: string): boolean {
	const octets = value.trim().split(".");
	return octets.length === 4 && octets.every((octet) => /^(0|[1-9]\d{0,2})$/.test(octet) && Number(octet) <= 255);
}

// The lines of the ZPL where a problem shows, so the report can point at them. A placeholder is found as {{name}};
// a command, as itself. A problem of the whole label (no token) has no line.
export function problemLines(zpl: string, token: string | null): number[] {
	if (!token) return [];
	const needle = token.startsWith("^") || token.startsWith("~") ? token : `{{${token}}}`;
	return zpl.split("\n").flatMap((line, index) => (line.includes(needle) ? [index + 1] : []));
}
