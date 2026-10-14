import type { OrderLine, OrderStatus } from "./types";

// What an order of the warehouse still needs, and what the screen offers, from its status and the person's permissions
// (order-fulfillment.md sections 6, 7a and 8). This is a pure decision: the buttons of the detail come from here, and
// the API checks every request again.

export type OrderAction = "assign" | "reassignOperator" | "validate" | "acceptDifference" | "cancel" | "transfer" | "returnStock" | "document";

// The permissions that gate each action, as the signed-in person holds them.
export interface OrderPermissions {
	assign: boolean; // orders.assign
	validate: boolean; // orders.validate
	cancel: boolean; // orders.cancel
	reassign: boolean; // orders.reassign
	acceptDifference: boolean; // orders.accept_difference
	view: boolean; // orders.view
	// locations.view: the return to stock is placed in a location, and only whoever reads locations is offered one.
	locations: boolean;
}

export interface OrderActionInput {
	status: OrderStatus;
	hasOperator: boolean;
	lines: Pick<OrderLine, "quantity" | "pickedQuantity">[];
	can: OrderPermissions;
}

// An order is open, and can be assigned, cancelled or reassigned, before it is validated.
export const isOpen = (status: OrderStatus): boolean => status === "CREATED" || status === "FULFILLING";

export const anyPicked = (lines: Pick<OrderLine, "pickedQuantity">[]): boolean => lines.some((line) => Number(line.pickedQuantity) > 0);

// A line the operator has not finished: its picked quantity is below what it asked for.
export const anyShort = (lines: Pick<OrderLine, "quantity" | "pickedQuantity">[]): boolean => lines.some((line) => Number(line.pickedQuantity) < Number(line.quantity));

export function orderActions(input: OrderActionInput): OrderAction[] {
	const { status, can } = input;
	const open = isOpen(status);
	const picked = anyPicked(input.lines);
	const actions: OrderAction[] = [];

	if (open && can.assign && !input.hasOperator) actions.push("assign");
	if (open && can.reassign && input.hasOperator) actions.push("reassignOperator");
	if (open && can.reassign && picked) actions.push("transfer");
	if (open && can.reassign && picked && can.locations) actions.push("returnStock");
	// Accepting a difference is the manager's judgement on a shortage the customer accepts (section 10).
	if (status === "FULFILLING" && can.acceptDifference && anyShort(input.lines)) actions.push("acceptDifference");
	if (status === "FULFILLED" && can.validate) actions.push("validate");
	// Cancelling is allowed while nothing has been picked (section 8).
	if (open && can.cancel && !picked) actions.push("cancel");
	// A validated order is locked: the receipt is the only thing left to do with it.
	if (status === "VALIDATED" && can.view) actions.push("document");

	return actions;
}

export const showsAction = (actions: OrderAction[], action: OrderAction): boolean => actions.includes(action);
