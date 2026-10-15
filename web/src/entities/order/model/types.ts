import type { UnitRef } from "@entities/stock";

// The statuses of an order, from the ORDER_STATUS catalog (order-fulfillment.md section 6).
export type OrderStatus = "CREATED" | "FULFILLING" | "FULFILLED" | "VALIDATED" | "CANCELLED";

export interface Ref {
	id: string;
	code: string | null;
	name: string | null;
}

// One material of an order: what it asked for, and how much of it has left the shelves.
export interface OrderLine {
	id: string;
	product: Ref;
	unit: UnitRef | null;
	quantity: number;
	pickedQuantity: number;
	[key: string]: unknown;
}

export interface Order {
	id: string;
	number: string;
	warehouse: Ref;
	customer: Ref;
	contact: Ref | null;
	dueDate: string;
	status: { code: OrderStatus; label: string | null };
	operator: Ref | null;
	createdBy: Ref | null;
	createdAt: string;
	validatedAt: string | null;
	validatedBy: Ref | null;
	// Only when one order is read.
	lines: OrderLine[] | null;
	[key: string]: unknown;
}

export interface OrderLineInput {
	productId: string;
	quantity: number;
}

export interface OrderInput {
	number: string;
	warehouseId: string;
	customerId: string;
	contactId: string | null;
	dueDate: string;
	lines: OrderLineInput[];
}

// One person an order can be assigned to: an active operator of its warehouse (order-fulfillment.md section 7a).
export interface OrderOperator {
	id: string;
	username: string;
	name: string;
}

// What the warehouse still has of a material, once the open orders have reserved theirs (section 5).
export interface AvailabilityItem {
	productId: string;
	available: number;
}

export interface TransferInput {
	lineId: string;
	targetOrderId: string;
	quantity: number;
	note: string;
}

export interface ReturnInput {
	lineId: string;
	locationId: string;
	quantity: number;
	note: string;
}

// One due date alert of the signed-in person (section 7a).
export interface OrderAlert {
	id: string;
	order: Order;
	kind: "BEFORE" | "DUE" | "OVERDUE";
	raisedOn: string;
}
