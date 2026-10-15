// F8-05 to F8-08 · unit: the fast flow's decisions that need no browser — the filters the list sends, the four fields
// of the modal and where each button goes, which line a repeated material adds to, and how the focus stays inside the
// dialog.
import { test, expect } from "@playwright/test";
import { captureTarget, captureSummary } from "../../src/entities/invoice/model/capture";
import { invoiceFilters, statusChip, openStatuses } from "../../src/entities/invoice/model/filters";
import { NEW_INVOICE_FIELDS, newInvoiceDestination, newInvoiceErrors, rememberedWorkspace } from "../../src/entities/invoice/model/newInvoice";
import { focusTrapIndex, firstInvalidField } from "../../src/shared/lib/focusTrap";

test("the search box and the chip become the filters of the request, combined", () => {
	// The default chip is Todas: no status filter, and no search until something is written.
	expect(invoiceFilters({ search: "", chip: "all" })).toEqual({});
	expect(invoiceFilters({ search: "   ", chip: "all" })).toEqual({});
	// The three chips: Todas asks for nothing, Cerradas for CLOSED, Abiertas for every status that is not closed.
	expect(invoiceFilters({ search: "", chip: "closed" })).toEqual({ status: ["CLOSED"] });
	expect(invoiceFilters({ search: "", chip: "open" })).toEqual({ status: openStatuses() });
	// The search travels as it was written, without the spaces around it, and combines with the chip.
	expect(invoiceFilters({ search: "  aceros  ", chip: "closed" })).toEqual({ search: "aceros", status: ["CLOSED"] });
});

test("a chip is read from the query of the list, and anything else is Todas", () => {
	expect(statusChip("open")).toBe("open");
	expect(statusChip("closed")).toBe("closed");
	expect(statusChip("all")).toBe("all");
	expect(statusChip("nope")).toBe("all");
	expect(statusChip(null)).toBe("all");
	expect(openStatuses()).toEqual(["OPEN", "PENDING", "IN_ASSIGNMENT"]);
});

const line = (id: string, productId: string) => ({ id, product: { id: productId } }) as never;

test("a material that is already a line adds to it instead of creating another (F8-07)", () => {
	expect(captureTarget([line("l1", "p1")], "p1")).toEqual({ kind: "sum", lineId: "l1" });
	expect(captureTarget([line("l1", "p1"), line("l2", "p2")], "p2")).toEqual({ kind: "sum", lineId: "l2" });
	expect(captureTarget([line("l1", "p1")], "p2")).toEqual({ kind: "add" });
	expect(captureTarget([], "p1")).toEqual({ kind: "add" });
});

test("the summary of the lines counts them and adds their quantities (F8-07)", () => {
	expect(captureSummary([])).toEqual({ lines: 0, pieces: 0 });
	expect(captureSummary([{ quantity: 100 } as never, { quantity: 20.5 } as never])).toEqual({ lines: 2, pieces: 120.5 });
});

test("the modal asks for four fields and nothing else, and each action goes where it says (F8-06)", () => {
	expect([...NEW_INVOICE_FIELDS]).toEqual(["number", "supplierId", "invoiceDate", "warehouseId"]);
	expect(newInvoiceDestination("save", "inv-1")).toBe("/invoices");
	expect(newInvoiceDestination("materials", "inv-1")).toBe("/invoices/inv-1");
});

test("the modal says which field is missing, in the order of the fields (F8-06)", () => {
	const complete = { number: "F-1", supplierId: "s1", invoiceDate: new Date(2026, 9, 15), warehouseId: "w1" };
	expect(newInvoiceErrors(complete)).toEqual({});
	expect(newInvoiceErrors({ ...complete, number: "  " })).toEqual({ number: "FIELD_REQUIRED" });
	// The first field with a problem takes the focus, from the top: the number before the supplier.
	expect(newInvoiceErrors({ ...complete, number: "", supplierId: "" })).toEqual({ number: "FIELD_REQUIRED", supplierId: "FIELD_REQUIRED" });
	expect(firstInvalidField(newInvoiceErrors(complete), NEW_INVOICE_FIELDS)).toBeNull();
	expect(firstInvalidField(newInvoiceErrors({ ...complete, supplierId: "" }), NEW_INVOICE_FIELDS)).toBe("supplierId");
});

test("what the session remembers: the last supplier and warehouse, and only those (F8-06)", () => {
	const store = new Map<string, string>();
	const storage = { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) };
	expect(rememberedWorkspace(storage)).toEqual({ supplierId: "", warehouseId: "" });
	rememberedWorkspace(storage, { supplierId: "s1", warehouseId: "w1" });
	expect(rememberedWorkspace(storage)).toEqual({ supplierId: "s1", warehouseId: "w1" });
	// A broken value never breaks the form.
	store.set("stockflow.invoice.supplier", "s2");
	expect(rememberedWorkspace(storage).supplierId).toBe("s2");
});

test("the focus stays inside the dialog and wraps at both ends (F8-08)", () => {
	// Forward from the last control goes back to the first, and backwards from the first goes to the last.
	expect(focusTrapIndex(3, 2, 1)).toBe(0);
	expect(focusTrapIndex(3, 0, -1)).toBe(2);
	expect(focusTrapIndex(3, 1, 1)).toBe(2);
	expect(focusTrapIndex(3, 1, -1)).toBe(0);
	// A dialog with a single control stays on it.
	expect(focusTrapIndex(1, 0, 1)).toBe(0);
	// Nothing focusable: no index.
	expect(focusTrapIndex(0, 0, 1)).toBeNull();
});
