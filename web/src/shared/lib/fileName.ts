// The name of a file the API answers, read from its `Content-Disposition` (RFC 6266): the plain `filename`, or the
// encoded `filename*` when it is the one there. Without a header the screen offers its own name.
export function fileNameOf(contentDisposition: string | null | undefined, fallback = ""): string {
	if (!contentDisposition) return fallback;
	const encoded = /filename\*\s*=\s*([^;]+)/i.exec(contentDisposition);
	if (encoded) {
		const value = encoded[1].trim().replace(/^UTF-8''/i, "").replace(/^"|"$/g, "");
		try {
			return decodeURIComponent(value);
		} catch {
			return value;
		}
	}
	const plain = /filename\s*=\s*"([^"]*)"|filename\s*=\s*([^;]+)/i.exec(contentDisposition);
	const name = (plain?.[1] ?? plain?.[2] ?? "").trim();
	return name.length > 0 ? name : fallback;
}
