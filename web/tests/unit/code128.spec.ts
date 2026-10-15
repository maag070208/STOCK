// The Code 128 encoder of the label preview: the bars a person sees are the bars the printer would print.
import { expect, test } from "@playwright/test";
import { code128Bars, code128Modules, code128Values, code128Width } from "../../src/features/label/model/code128";

test("the modules are the standard patterns, and the stop ends them", () => {
	// "A" is start B ("211214"), the value of A in code set B ("111323"), its check character ("131123") and the stop.
	// The widths are what makes the barcode readable: a wrong one is a barcode a scanner refuses.
	expect(code128Modules("A").join("")).toBe("2112141113231311232331112");
	// Every element is a width between one and four modules: the array is not a string of digits.
	expect(code128Modules("A").every((width) => width >= 1 && width <= 4)).toBe(true);
	expect(code128Modules("A").slice(-7)).toEqual([2, 3, 3, 1, 1, 1, 2]);
});

test("the check character is the weighted sum modulo 103", () => {
	// "A" is value 33 in code set B: the start character (104) plus one weighted value.
	expect(code128Values("A")).toEqual([104, 33, 34, 106]);
	// "1234" weights each value by its position, the first one by one.
	expect(code128Values("1234")).toEqual([104, 17, 18, 19, 20, 88, 106]);
});

test("a barcode is eleven modules per symbol, and thirteen for the stop", () => {
	// "A" needs three symbols (start, the character and the check) and the stop.
	expect(code128Width("A")).toBe(3 * 11 + 13);
	// A longer value adds one symbol each: no compression in code set B.
	expect(code128Width("AB")).toBe(4 * 11 + 13);
	// An empty value is still a barcode: start, check and stop.
	expect(code128Width("")).toBe(2 * 11 + 13);
	// Every symbol is three bars and three spaces, and the stop is four bars and three spaces: the count is exact, not
	// half of the modules (a bar can be wider than a space).
	const bars = code128Bars("7501234567890");
	// Fifteen symbols (start, thirteen digits and the check) plus the stop.
	expect(bars.length).toBe(15 * 3 + 4);
	expect(bars[0].start).toBe(0);
	expect(bars.at(-1)!.start + bars.at(-1)!.width).toBe(code128Width("7501234567890"));
});

test("a character outside the printable range is drawn, not dropped", () => {
	// A barcode that silently loses a character is worse than one that shows a question mark.
	expect(code128Values("a\tb")).toEqual([104, 65, 31, 66, expect.any(Number), 106]);
});
