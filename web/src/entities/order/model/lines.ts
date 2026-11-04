import type { AvailabilityItem } from "./types";

// The lines of a new order (order-fulfillment.md section 4): what the warehouse has of the chosen material, and the
// quantity the creator may ask for. The screen shows the availability and keeps the quantity at or below it before the
// order is sent; the API checks it again, under the lock, when the order is created.

export type LineError = "FIELD_REQUIRED" | "QUANTITY_INVALID" | "STOCK_INSUFFICIENT" | "DUPLICATE_PRODUCT";

// What the warehouse still has of one material, or null while the API has not answered for it.
export function availableOf(items: AvailabilityItem[], productId: string | null | undefined): number | null {
	if (!productId) return null;
	const found = items.find((item) => item.productId === productId);
	return found ? Number(found.available) : null;
}

// A quantity is a positive decimal with at most the decimals its unit allows, and at most what is available when that
// is known. The decimals rule mirrors the API's QUANTITY_INVALID: 1.5 of a whole piece is the same refusal as zero.
export function lineError(quantity: string, available: number | null, decimals = 0): LineError | null {
	if (!quantity.trim()) return "FIELD_REQUIRED";
	const value = Number(quantity);
	if (!Number.isFinite(value) || value <= 0) return "QUANTITY_INVALID";
	const scaled = value * 10 ** decimals;
	if (Math.abs(scaled - Math.round(scaled)) > 1e-9) return "QUANTITY_INVALID";
	if (available !== null && value > available) return "STOCK_INSUFFICIENT";
	return null;
}

// The materials this order asks for more than once: the API refuses a repeated line, so the form catches it while it
// is written and the picker stops offering what another line already took.
export function repeatedProducts(productIds: (string | null | undefined)[]): Set<string> {
	const seen = new Set<string>();
	const repeated = new Set<string>();
	for (const id of productIds) {
		if (!id) continue;
		if (seen.has(id)) repeated.add(id);
		seen.add(id);
	}
	return repeated;
}
