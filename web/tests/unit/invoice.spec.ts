// F4-20 and F4-21 · unit: the assignment form blocks a split whose total differs from the line before submitting;
// the adjustment asks for a note before it can be sent.
import { test, expect } from "@playwright/test";
import { canAdjust, sharesError, sharesTotal } from "../../src/entities/invoice/model/shares";

const share = (operatorId: string, quantity: string) => ({ operatorId, quantity });

test("a split whose shares add up to the line can be sent", () => {
	expect(sharesError("100", [share("a", "60"), share("b", "40")])).toBeNull();
	expect(sharesTotal([share("a", "60"), share("b", "40")])).toBe(100);
});

test("a split whose total differs from the line is blocked", () => {
	expect(sharesError("100", [share("a", "60"), share("b", "30")])).toBe("ASSIGNMENT_TOTAL_MISMATCH");
	expect(sharesError("100", [])).toBe("FIELD_REQUIRED");
});

test("each share needs an operator, once, and a positive quantity", () => {
	expect(sharesError("100", [share("", "100")])).toBe("FIELD_REQUIRED");
	expect(sharesError("100", [share("a", "0"), share("b", "100")])).toBe("QUANTITY_INVALID");
	expect(sharesError("100", [share("a", "50"), share("a", "50")])).toBe("DUPLICATE_VALUE");
});

test("an adjustment needs a note", () => {
	expect(canAdjust("")).toBe(false);
	expect(canAdjust("   ")).toBe(false);
	expect(canAdjust("El proveedor envió menos")).toBe(true);
});
