// F5-16 and F5-17 · unit: the lines of a new order show what the warehouse has and keep every quantity at or below it
// before the order is sent, and the detail offers each action only when the permission and the status allow it
// (order-fulfillment.md sections 4, 6, 7a and 8).
import { test, expect } from "@playwright/test";
import { availableOf, lineError } from "../../src/entities/order/model/lines";
import { orderActions, type OrderPermissions } from "../../src/entities/order/model/actions";
import type { OrderStatus } from "../../src/entities/order/model/types";

const everything: OrderPermissions = {
	assign: true,
	validate: true,
	cancel: true,
	reassign: true,
	acceptDifference: true,
	view: true,
	locations: true,
};

const line = (quantity: number, pickedQuantity: number) => ({ quantity, pickedQuantity });
const actions = (status: OrderStatus, options: { hasOperator?: boolean; lines?: ReturnType<typeof line>[]; can?: Partial<OrderPermissions> } = {}) =>
	orderActions({ status, hasOperator: options.hasOperator ?? false, lines: options.lines ?? [line(10, 0)], can: { ...everything, ...options.can } });

test("a line shows the availability of the order's warehouse, and above it is blocked before sending", () => {
	const items = [
		{ productId: "screws", available: 140 },
		{ productId: "nails", available: 0 },
	];
	expect(availableOf(items, "screws")).toBe(140);
	expect(availableOf(items, "nails")).toBe(0);
	// A material the API has not answered for: the screen says nothing about it, and does not block on it.
	expect(availableOf(items, "bolts")).toBeNull();
	expect(availableOf(items, null)).toBeNull();

	expect(lineError("150", availableOf(items, "screws"))).toBe("STOCK_INSUFFICIENT");
	expect(lineError("140", availableOf(items, "screws"))).toBeNull();
	expect(lineError("1", availableOf(items, "nails"))).toBe("STOCK_INSUFFICIENT");
	// The quantity itself has to be a positive number, whatever the availability.
	expect(lineError("", 140)).toBe("FIELD_REQUIRED");
	expect(lineError("   ", 140)).toBe("FIELD_REQUIRED");
	expect(lineError("0", 140)).toBe("QUANTITY_INVALID");
	expect(lineError("-5", 140)).toBe("QUANTITY_INVALID");
	expect(lineError("10.5", 140)).toBeNull();
	// Without an answer yet, only the quantity itself is checked: the API decides about the stock.
	expect(lineError("10", availableOf(items, "bolts"))).toBeNull();
});

test("an open order is assigned, cancelled or reassigned, and a validated one only offers its receipt", () => {
	// Created, nobody assigned: the first assignment and, with nothing picked, the cancellation.
	expect(actions("CREATED")).toEqual(["assign", "cancel"]);
	// Created, already assigned: the operator changes, and it can still be cancelled.
	expect(actions("CREATED", { hasOperator: true })).toEqual(["reassignOperator", "cancel"]);

	// Fulfilling, with material picked and a line still short: the reassignments and the difference.
	expect(actions("FULFILLING", { hasOperator: true, lines: [line(30, 20)] })).toEqual(["reassignOperator", "transfer", "returnStock", "acceptDifference"]);

	// Fulfilling and complete: the picked material can still change hands, and it cannot be cancelled.
	expect(actions("FULFILLING", { hasOperator: true, lines: [line(10, 10)] })).toEqual(["reassignOperator", "transfer", "returnStock"]);

	// Fulfilled: only the validation is left.
	expect(actions("FULFILLED", { hasOperator: true, lines: [line(10, 10)] })).toEqual(["validate"]);

	// Validated: the order is locked, so its receipt is the only action, whatever else the person may do.
	expect(actions("VALIDATED", { hasOperator: true, lines: [line(10, 10)] })).toEqual(["document"]);
	// Cancelled: there is nothing left to do with it.
	expect(actions("CANCELLED")).toEqual([]);
});

test("an action the person cannot perform is not offered, and neither is a return without locations to offer", () => {
	expect(actions("CREATED", { can: { assign: false } })).toEqual(["cancel"]);
	expect(actions("FULFILLED", { can: { validate: false } })).toEqual([]);
	expect(actions("FULFILLING", { hasOperator: true, lines: [line(30, 20)], can: { reassign: false } })).toEqual(["acceptDifference"]);
	expect(actions("FULFILLING", { hasOperator: true, lines: [line(30, 20)], can: { acceptDifference: false } })).toEqual(["reassignOperator", "transfer", "returnStock"]);
	// Office reassigns but reads no locations: it can move the picked material to another order, not put it back.
	expect(actions("FULFILLING", { hasOperator: true, lines: [line(30, 20)], can: { locations: false } })).toEqual(["reassignOperator", "transfer", "acceptDifference"]);
	expect(actions("CREATED", { can: { cancel: false } })).toEqual(["assign"]);
});
