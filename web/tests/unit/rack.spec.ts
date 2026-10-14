// F2-11 · unit: the rack form shows the codes it will create before submitting, with the API's own rules.
import { test, expect } from "@playwright/test";
import { existingCodes, rackCodes, rackErrors } from "../../src/entities/warehouse/model/rack";

test("aisle K, rack 05 and three levels will create K05A, K05B and K05C", () => {
	expect(rackCodes({ aisle: "k", rack: "05", levels: "3" })).toEqual(["K05A", "K05B", "K05C"]);
	expect(rackCodes({ aisle: "K", rack: "05", levels: "1" })).toEqual(["K05A"]);
	expect(rackCodes({ aisle: "A", rack: "00", levels: "26" }).at(-1)).toBe("A00Z");
});

test("while a part is missing or wrong, the form shows no codes", () => {
	expect(rackCodes({ aisle: "", rack: "05", levels: "3" })).toEqual([]);
	expect(rackCodes({ aisle: "KK", rack: "05", levels: "3" })).toEqual([]);
	expect(rackCodes({ aisle: "K", rack: "5", levels: "3" })).toEqual([]);
	expect(rackCodes({ aisle: "K", rack: "05", levels: "27" })).toEqual([]);
	expect(rackCodes({ aisle: "K", rack: "05", levels: "2.5" })).toEqual([]);
});

test("each field has the rule of the API: one letter, two digits, 1 to 26 levels", () => {
	expect(rackErrors({ aisle: "", rack: "", levels: "" })).toEqual({ aisle: "FIELD_REQUIRED", rack: "FIELD_REQUIRED", levels: "FIELD_REQUIRED" });
	expect(rackErrors({ aisle: "1", rack: "105", levels: "0" })).toEqual({ aisle: "FORMAT_INVALID", rack: "FORMAT_INVALID", levels: "FORMAT_INVALID" });
	expect(rackErrors({ aisle: "k", rack: "05", levels: "3" })).toEqual({});
});

test("the codes that already exist are read from the API's answer", () => {
	expect(existingCodes({ "codes.K05B": "x", "codes.K05A": "x", aisle: "x" })).toEqual(["K05A", "K05B"]);
	expect(existingCodes({})).toEqual([]);
});
