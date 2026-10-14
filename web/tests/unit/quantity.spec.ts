// F4-19 · unit: a quantity shows with its unit's decimals, in the interface's language.
import { test, expect } from "@playwright/test";
import { formatQuantity } from "../../src/shared/lib/format";

test("a quantity shows exactly the decimals its unit allows", () => {
	expect(formatQuantity(10914130, 0, "es")).toBe("10,914,130");
	expect(formatQuantity(2.5, 2, "es")).toBe("2.50");
	expect(formatQuantity(1.2345, 3, "en")).toBe("1.235");
	expect(formatQuantity("300.000000", 0, "es")).toBe("300");
});

test("a unit without decimals given shows whole numbers", () => {
	expect(formatQuantity(7, null, "es")).toBe("7");
});
