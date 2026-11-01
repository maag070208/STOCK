import type { InvoiceStatus } from "./types";

// The color of each status of an invoice: open is still being captured, completed is done. Shared by the list, the
// detail and the dashboard, so the same status is the same color everywhere.
export const STATUS_COLOR: Record<InvoiceStatus, "gray" | "warning" | "info" | "success" | "danger"> = {
	OPEN: "gray",
	PENDING: "warning",
	IN_ASSIGNMENT: "info",
	CLOSED: "success",
	CANCELLED: "danger",
};

// Where an invoice is in its way through the warehouse, and so what comes next. Cancelled invoices are off the way.
export type InvoiceStage = "capture" | "assign" | "count" | "resolve" | "place" | "done";

export function invoiceStage(invoice: { status: { code: InvoiceStatus }; lines: { assignments: { countedQuantity: number | null; outcome: string | null; settled?: boolean; placed: boolean }[] }[] | null }): InvoiceStage | null {
	if (invoice.status.code === "CANCELLED") return null;
	if (invoice.status.code === "CLOSED") return "done";
	const lines = invoice.lines ?? [];
	const assignments = lines.flatMap((line) => line.assignments);
	if (lines.length === 0) return "capture";
	if (lines.some((line) => line.assignments.length === 0)) return "assign";
	if (assignments.some((assignment) => assignment.countedQuantity === null)) return "count";
	if (assignments.some((assignment) => assignment.outcome === "DISCREPANCY" && !assignment.settled)) return "resolve";
	return "place";
}
