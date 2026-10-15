import type { UnitRef } from "@entities/stock";

export interface Ref {
	id: string;
	code: string | null;
	name: string | null;
}

export interface Placement {
	id: string;
	locationId: string;
	locationCode: string | null;
	quantity: number;
	placedAt: string;
}

// One line's share for one operator (material-entry.md section 9).
export interface Assignment {
	id: string;
	lineId: string;
	operator: Ref;
	expectedQuantity: number;
	countedQuantity: number | null;
	difference: number | null;
	outcome: "MATCH" | "DISCREPANCY" | null;
	countNotes: string | null;
	countedAt: string | null;
	placedQuantity: number;
	placed: boolean;
	placements: Placement[];
}

export interface InvoiceLine {
	id: string;
	product: Ref;
	unit: UnitRef | null;
	quantity: number;
	assignments: Assignment[];
}

export type InvoiceStatus = "OPEN" | "PENDING" | "IN_ASSIGNMENT" | "CLOSED";

export interface Invoice {
	id: string;
	number: string;
	supplier: Ref;
	invoiceDate: string;
	warehouse: Ref;
	status: { code: InvoiceStatus; label: string | null };
	createdAt: string;
	// Only when one invoice is read.
	lines: InvoiceLine[] | null;
	[key: string]: unknown;
}

export interface LineInput {
	productId: string;
	quantity: number;
	assignments?: { operatorId: string; quantity: number }[];
}

export interface InvoiceInput {
	number: string;
	supplierId: string;
	invoiceDate: string;
	warehouseId: string;
	lines: LineInput[];
}

export interface Discrepancy {
	assignmentId: string;
	invoice: Ref;
	warehouse: Ref;
	product: Ref;
	operator: Ref;
	expectedQuantity: number;
	countedQuantity: number;
	difference: number;
	countNotes: string | null;
	countedAt: string | null;
	[key: string]: unknown;
}

export interface StaffMember {
	id: string;
	username: string;
	name: string;
}
