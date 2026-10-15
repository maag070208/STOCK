// F6-06 · unit: the kardex shows the running balance the API sends, and never adds quantities up in the browser. The
// running balance of a movement depends on every movement before it, so only the report can compute it (reports.md
// section 3); this module carries it as it came.
import { test, expect } from "@playwright/test";
import { kardexClosing, kardexRows } from "../../src/entities/report/model/kardex";
import { fileNameOf } from "../../src/shared/lib/fileName";
import type { KardexPage, MovementReportRow } from "../../src/entities/report/model/types";

const ref = (code: string, name = code) => ({ id: `id-${code}`, code, name });

const movement = (seq: number, change: Partial<MovementReportRow>): MovementReportRow => ({
	seq,
	occurredAt: `2026-10-1${seq}T10:00:00Z`,
	type: "RECEIPT",
	typeLabel: "Recepción",
	warehouse: ref("CENTRAL"),
	location: ref(`K05A`),
	product: ref("M1", "Tornillo"),
	unit: { code: "PIECE", label: "Pieza", decimalPlaces: 0 },
	quantity: 10,
	incoming: true,
	runningLocation: 10,
	runningWarehouse: 10,
	user: ref("aamaro", "A. Amaro"),
	reason: "prueba",
	sourceType: "test",
	sourceId: "1",
	...change,
});

// The running balances of this page are deliberately NOT the sums of the rows before them: whoever computes them in the
// browser would show something else, and the test would catch it.
const page: KardexPage = {
	data: [
		movement(1, { incoming: true, quantity: 10, runningLocation: 10, runningWarehouse: 999 }),
		movement(2, { incoming: false, quantity: 4, runningLocation: 6, runningWarehouse: 998 }),
		movement(3, { incoming: true, quantity: 100, runningLocation: 106, runningWarehouse: 997 }),
	],
	total: 3,
	page: 1,
	pageIndex: 0,
	totalPages: 1,
	totalCount: 3,
	limit: 20,
	hasPreviousPage: false,
	hasNextPage: false,
	product: { product: ref("M1", "Tornillo"), warehouse: ref("CENTRAL"), unit: { code: "PIECE", label: "Pieza", decimalPlaces: 0 }, closing: 1234 },
};

test("the kardex carries the running balance of the API, and does not compute it", () => {
	const rows = kardexRows(page);
	expect(rows.map((row) => row.runningWarehouse)).toEqual([999, 998, 997]);
	expect(rows.map((row) => row.runningLocation)).toEqual([10, 6, 106]);
	// The split of a movement comes from the direction the API sent, not from its sign.
	expect(rows.map((row) => [row.incoming, row.outgoing])).toEqual([
		[10, null],
		[null, 4],
		[100, null],
	]);
	// The closing balance is the one the report answered for the product.
	expect(kardexClosing(page)).toBe(1234);
	// The material, the location and the type are the API's own texts.
	expect(rows[0]).toMatchObject({ key: "1", type: "Recepción", location: "K05A", user: "A. Amaro" });
});

test("a kardex without an answer is empty, and keeps the closing the API gave", () => {
	expect(kardexRows(null)).toEqual([]);
	expect(kardexClosing(null)).toBeNull();
	expect(kardexClosing({ ...page, product: null })).toBeNull();
});

test("the name of an exported file is read from the header, with a fallback", () => {
	expect(fileNameOf('attachment; filename="existencias-2026-10-14.xlsx"')).toBe("existencias-2026-10-14.xlsx");
	expect(fileNameOf("attachment; filename=kardex.pdf")).toBe("kardex.pdf");
	expect(fileNameOf("attachment; filename*=UTF-8''movimientos%20oct.pdf")).toBe("movimientos oct.pdf");
	// Without a name, the screen offers its own.
	expect(fileNameOf(null, "reporte.xlsx")).toBe("reporte.xlsx");
	expect(fileNameOf("", "reporte.pdf")).toBe("reporte.pdf");
});
