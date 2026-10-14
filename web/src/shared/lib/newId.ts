// An id for the browser. crypto.randomUUID needs a secure context, and the web is sometimes opened over plain HTTP (web.md section 9).
export function newId(): string {
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}
