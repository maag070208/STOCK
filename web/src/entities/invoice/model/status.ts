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
