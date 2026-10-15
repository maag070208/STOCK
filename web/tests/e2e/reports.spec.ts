// F6-06 · e2e: the report screens. The office opens the current stock, filters it by material, takes it away as a file,
// and reads the kardex of a material, whose running balance is the one the API computed (reports.md sections 3 and 4).
// Nobody without reports.view sees anything but the 403 page (section 2).
import { test, expect, type Page } from "@playwright/test";
import { adminToken, call, newUser, signInAs, uniqueName } from "../support/api";
import { ordersFixture } from "../support/orders";

// The search box a column's filter draws in the header, as the kit renders it.
function filterOf(page: Page, testId: string, column: RegExp) {
	return page.getByTestId(testId).getByRole("columnheader", { name: column }).getByPlaceholder("Buscar...");
}

test("the office filters the current stock, exports it, and reads the kardex of a material", async ({ page }) => {
	const f = await ordersFixture();
	const screws = await f.product("Tornillo de reportes");
	await f.stock(screws.id, 40);
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);

	// The current stock of the warehouse, filtered from the screen: the material and what it holds.
	await page.goto("/#/reports/stock");
	await expect(page.getByRole("heading", { name: "Existencias actuales" })).toBeVisible();
	await filterOf(page, "stock-report", /Material/).fill(screws.code);
	const row = page.getByTestId("stock-report").getByRole("row").filter({ hasText: screws.code });
	await expect(row).toContainText(f.warehouse.code);
	await expect(row).toContainText("40");
	// The total the screen groups by is the sum of the rows it lists.
	await expect(page.getByTestId("stock-total")).toContainText("40");

	// The filter this screen manages itself, the one a column filter does not carry. The screen asks again with it.
	await Promise.all([
		page.waitForResponse((response) => response.url().endsWith("/reports/stock") && response.request().postData()?.includes(f.warehouse.id) === true),
		page.locator('select[name="warehouseId"]').selectOption(f.warehouse.id),
	]);
	await expect(page.getByTestId("stock-total")).toContainText("40");

	// The export is the file the API builds from the same filters, never one the browser writes: the filters of the
	// screen travel with the request, so a file can never hold rows the screen left out.
	const asked: string[] = [];
	page.on("request", (request) => {
		if (request.url().includes("export")) asked.push(`${request.method()} ${request.url()} ${request.postData() ?? "<sin cuerpo>"}`);
	});
	const [xlsx] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "XLSX" }).click()]);
	expect(asked.filter((body) => body.includes(f.warehouse.id)).length, asked.join("\n")).toBeGreaterThan(0);
	expect(xlsx.suggestedFilename()).toMatch(/\.xlsx$/);
	const [pdf] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "PDF" }).click()]);
	expect(pdf.suggestedFilename()).toMatch(/\.pdf$/);

	// The kardex of the material: what the warehouse did with it, with the running balance the API sent.
	await page.goto("/#/reports/kardex");
	await expect(page.getByRole("heading", { name: "Kardex" })).toBeVisible();
	await page.getByPlaceholder("Busca por código o nombre").fill(screws.code);
	await page.getByText(`${screws.code} · ${screws.name}`).first().click();
	const kardex = page.getByTestId("kardex-report");
	await expect(kardex.locator('[data-running="40"]').first()).toBeVisible();
	await expect(page.getByTestId("kardex-closing")).toContainText("40");
});

test("the movements report answers what came in for a material", async ({ page }) => {
	const f = await ordersFixture();
	const product = await f.product("Clavo de reportes");
	await f.stock(product.id, 25);
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);

	await page.goto("/#/reports/movements");
	await expect(page.getByRole("heading", { name: "Movimientos" })).toBeVisible();
	await page.getByPlaceholder("Busca por código o nombre").fill(product.code);
	await page.getByText(`${product.code} · ${product.name}`).first().click();
	await expect(page.getByTestId("movements-report").getByRole("row").filter({ hasText: product.code })).toContainText("25");
	await expect(page.getByTestId("movements-total")).toContainText("Entradas");
});

test("the invoices report answers the entry that put a material on the shelf", async ({ page }) => {
	const f = await ordersFixture();
	const product = await f.product("Clavo de reportes");
	await f.stock(product.id, 25);
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);

	await page.goto("/#/reports/invoices");
	await expect(page.getByRole("heading", { name: "Facturas y entradas" })).toBeVisible();
	await page.locator('input[name="supplierId"]').fill(f.supplier.code);
	await page.locator("div.max-h-60").last().getByText(f.supplier.code, { exact: false }).first().click();
	await expect(page.getByTestId("invoices-report").getByRole("row").filter({ hasText: product.code })).toContainText("25");
});

test("the orders report answers the orders of a period and a customer", async ({ page }) => {
	const f = await ordersFixture();
	const product = await f.product("Clavo de reportes");
	await f.stock(product.id, 25);
	const [operator] = f.operators;
	await f.order({ number: uniqueName("ORD-").toUpperCase(), productId: product.id, quantity: 10, operatorId: operator.id });
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);

	await page.goto("/#/reports/orders");
	await expect(page.getByRole("heading", { name: "Órdenes" })).toBeVisible();
	await page.locator('input[name="customerId"]').fill(f.customer.code);
	await page.locator("div.max-h-60").last().getByText(f.customer.code, { exact: false }).first().click();
	await expect(page.getByTestId("orders-report").getByRole("row").filter({ hasText: product.code })).toContainText("10");
});

test("someone without reports.view sees the 403 page", async ({ page }) => {
	const token = await adminToken();
	// A role of its own with web access and nothing else: no seed role has web access without reports.view.
	const role = uniqueName("T").toUpperCase();
	await call("POST", "/roles", { token, body: { key: role, name: "Rol de prueba" } });
	await call("PUT", "/roles/matrix", { token, body: { changes: [{ roleKey: role, permissionKey: "web.access", scope: "ALL" }] } });
	const nobody = await newUser([role]);
	await signInAs(page, nobody);
	await page.goto("/#/reports/stock");
	await expect(page.getByTestId("forbidden")).toBeVisible();
});
