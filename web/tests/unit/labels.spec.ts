// F3-11 and F3-12 · unit: the editor points each problem of its report at the lines of the ZPL, and the print and
// printer forms check what the API checks again: 1 to 100 copies, and an IPv4 address.
import { test, expect } from "@playwright/test";
import { copiesError, isIpAddress, problemLines } from "../../src/entities/label/model/rules";

const zpl = "^XA\n^FO30,30^FD{{name}}^FS\n^HH\n^FO30,90^FD{{rack}}^FS\n^XZ";

test("a forbidden command and an unknown placeholder point at their lines; a problem of the whole label has none", () => {
	expect(problemLines(zpl, "^HH")).toEqual([3]);
	expect(problemLines(zpl, "rack")).toEqual([4]);
	expect(problemLines("^XA^FD{{rack}}^FS\n^FD{{rack}}^FS^XZ", "rack")).toEqual([1, 2]);
	expect(problemLines(zpl, null)).toEqual([]);
	// A placeholder is matched as {{name}}: the word alone elsewhere in the text is not it.
	expect(problemLines("^XA^FDrack^FS^XZ", "rack")).toEqual([]);
});

test("copies default to 1 and go from 1 to 100, whole", () => {
	expect(copiesError("1")).toBeUndefined();
	expect(copiesError("100")).toBeUndefined();
	expect(copiesError("")).toBe("required");
	expect(copiesError("0")).toBe("copiesRange");
	expect(copiesError("101")).toBe("copiesRange");
	expect(copiesError("2.5")).toBe("copiesRange");
	expect(copiesError("-3")).toBe("copiesRange");
});

test("a printer's address is four octets from 0 to 255", () => {
	expect(isIpAddress("192.168.1.40")).toBe(true);
	expect(isIpAddress("0.0.0.0")).toBe(true);
	expect(isIpAddress("192.168.1")).toBe(false);
	expect(isIpAddress("192.168.1.256")).toBe(false);
	expect(isIpAddress("192.168.01.4")).toBe(false);
	expect(isIpAddress("zebra.local")).toBe(false);
});
