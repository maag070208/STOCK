import type { InvoiceLine } from "./types";

// The quick capture bar of the detail (F8-07). A material is one line per invoice, so a material that is already a line
// adds to it instead of creating another: the API refuses a repeated one with DUPLICATE_VALUE.
export type CaptureTarget = { kind: "add" } | { kind: "sum"; lineId: string };

export function captureTarget(lines: Pick<InvoiceLine, "id" | "product">[], productId: string): CaptureTarget {
	const found = lines.find((line) => line.product.id === productId);
	return found ? { kind: "sum", lineId: found.id } : { kind: "add" };
}

// The summary under the table: how many lines, and how many pieces they add up to.
export function captureSummary(lines: Pick<InvoiceLine, "quantity">[]): { lines: number; pieces: number } {
	return { lines: lines.length, pieces: lines.reduce((total, line) => total + Number(line.quantity), 0) };
}

// The quantity that a repeated material leaves on its line: what it had plus what was just captured.
export function addedQuantity(current: number, added: number): number {
	return Number(current) + Number(added);
}
