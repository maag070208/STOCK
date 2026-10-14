import type { AvailabilityItem } from "./types";

// The lines of a new order (order-fulfillment.md section 4): what the warehouse has of the chosen material, and the
// quantity the creator may ask for. The screen shows the availability and keeps the quantity at or below it before the
// order is sent; the API checks it again, under the lock, when the order is created.

export type LineError = "FIELD_REQUIRED" | "QUANTITY_INVALID" | "STOCK_INSUFFICIENT";

// What the warehouse still has of one material, or null while the API has not answered for it.
export function availableOf(items: AvailabilityItem[], productId: string | null | undefined): number | null {
	if (!productId) return null;
	const found = items.find((item) => item.productId === productId);
	return found ? Number(found.available) : null;
}

// A quantity is a positive decimal, and at most what is available when that is known.
export function lineError(quantity: string, available: number | null): LineError | null {
	const value = Number(quantity);
	if (!quantity.trim()) return "FIELD_REQUIRED";
	if (!Number.isFinite(value) || value <= 0) return "QUANTITY_INVALID";
	if (available !== null && value > available) return "STOCK_INSUFFICIENT";
	return null;
}
