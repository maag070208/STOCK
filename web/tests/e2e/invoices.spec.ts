// F4-20 and F4-21 · e2e: office creates an invoice and assigns it after saving (path A), then another assigned before
// saving (path B); the detail shows the status. Discrepancies are adjusted by who may resolve them, and only them.
import { test, expect, type Page } from "@playwright/test";
import { call, newUser, signInAs, uniqueName } from "../support/api";
import { entriesFixture } from "../support/entries";

async function pickProduct(page: Page, index: number, product: { code: string; name: string }) {
	const line = page.locator(`[data-line="${index}"]`);
	await line.getByPlaceholder("Busca por código o nombre").fill(product.code);
	await page.getByText(`${product.code} · ${product.name}`).first().click();
}

async function header(page: Page, number: string, supplierId: string, warehouseId: string) {
	await page.getByLabel(/^Número de factura/).fill(number);
	await page.locator('select[name="supplierId"]').selectOption(supplierId);
	await page.locator('select[name="warehouseId"]').selectOption(warehouseId);
}

test("office creates an invoice and assigns it after saving, then another one assigned before saving", async ({ page }) => {
	const f = await entriesFixture();
	const [a, b] = f.operators;
	const screws = await f.product("Tornillo de prueba");
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/invoices");
	await page.getByRole("button", { name: "Nueva factura" }).click();

	// An inactive supplier is not offered for a new invoice.
	await expect(page.locator('select[name="supplierId"] option', { hasText: f.inactiveSupplier.name })).toHaveCount(0);
	const numberA = uniqueName("FA-").toUpperCase();
	await header(page, numberA, f.supplier.id, f.warehouse.id);
	await page.getByRole("button", { name: "Agregar material" }).click();
	await pickProduct(page, 0, screws);
	await page.locator('input[name="quantity-0"]').fill("100");
	await page.getByRole("button", { name: "Guardar factura" }).click();

	// Path A: saved OPEN, assigned on the detail.
	const status = page.getByTestId("invoice-header");
	await expect(status).toContainText("Abierta");
	await page.getByRole("button", { name: "Asignar" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.locator('select[name="operator-0"]').selectOption(a.id);
	await dialog.locator('input[name="share-0"]').fill("60");
	await dialog.getByRole("button", { name: "Agregar operador" }).click();
	await dialog.locator('select[name="operator-1"]').selectOption(b.id);
	await dialog.locator('input[name="share-1"]').fill("30");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	// The split does not add up to the line: it is blocked before it is sent.
	await expect(dialog.getByText("Las cantidades deben sumar la cantidad de la línea.")).toBeVisible();
	await dialog.locator('input[name="share-1"]').fill("40");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(status).toContainText("Pendiente");
	const lines = page.getByTestId("invoice-lines");
	await expect(lines.locator(`[data-assignment="${a.name}"]`)).toContainText("60");
	await expect(lines.locator(`[data-assignment="${b.name}"]`)).toContainText("40");

	// Path B: assigned before saving, saved once.
	await page.goto("/#/invoices/new");
	await header(page, uniqueName("FB-").toUpperCase(), f.supplier.id, f.warehouse.id);
	await page.getByRole("button", { name: "Agregar material" }).click();
	await pickProduct(page, 0, screws);
	await page.locator('input[name="quantity-0"]').fill("100");
	await page.getByText("Asignar ahora").click();
	await page.locator('[data-testid="shares-0"] select[name="operator-0"]').selectOption(a.id);
	await page.getByRole("button", { name: "Guardar factura" }).click();
	await expect(status).toContainText("Pendiente");
	await expect(lines.locator(`[data-assignment="${a.name}"]`)).toContainText("100");
});

test("a discrepancy is adjusted by the warehouse's manager or by office, and not by someone without the permission", async ({ page, browser }) => {
	const f = await entriesFixture();
	const [a] = f.operators;
	const nails = await f.product("Clavo de prueba");
	const body = (number: string) => ({
		number, supplierId: f.supplier.id, invoiceDate: new Date().toISOString().slice(0, 10), warehouseId: f.warehouse.id,
		lines: [{ productId: nails.id, quantity: 100, assignments: [{ operatorId: a.id, quantity: 100 }] }],
	});
	const created = await fetch("http://localhost:8080/api/v1/invoices", {
		method: "POST",
		headers: { "Content-Type": "application/json", Authorization: `Bearer ${f.token}`, "X-Client-Channel": "web", "Idempotency-Key": uniqueName("k") },
		body: JSON.stringify(body(uniqueName("FD-").toUpperCase())),
	}).then((r) => r.json());
	const assignment = created.lines[0].assignments[0].id as string;
	// The operator signs in on the app and counts 80 of the 100.
	const login = await call("POST", "/auth/login", { body: { username: a.username, password: a.password }, channel: "app" });
	await call("POST", `/receipts/assignments/${assignment}/count`, { token: login.json.token, body: { countedQuantity: 80 }, channel: "app" });

	// Someone who can see invoices but not resolve discrepancies sees no action.
	const reader = await newUser(["OFFICE"]);
	await call("PUT", `/users/${reader.id}/permissions/discrepancies.resolve`, { token: f.token, body: { scope: "NONE", reason: "Solo consulta" } });
	const readerPage = await browser.newPage();
	await signInAs(readerPage, reader);
	await readerPage.goto(`/#/invoices/${created.id}`);
	await expect(readerPage.getByTestId("invoice-lines")).toContainText("Diferencia");
	await expect(readerPage.getByRole("button", { name: "Ajustar" })).toHaveCount(0);
	await readerPage.goto("/#/discrepancies");
	await expect(readerPage.getByTestId("forbidden")).toBeVisible();
	await readerPage.close();

	// The manager of the warehouse adjusts it from the list, with a note.
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	await call("PUT", `/users/${manager.id}/warehouses`, { token: f.token, body: { warehouseIds: [f.warehouse.id] } });
	await signInAs(page, manager);
	await page.goto("/#/discrepancies");
	const row = page.getByTestId("discrepancies").getByRole("row").filter({ hasText: created.number });
	await expect(row).toContainText("-20");
	await row.getByRole("button", { name: "Ajustar" }).click();
	const dialog = page.getByRole("dialog");
	await expect(dialog.getByRole("button", { name: "Ajustar" })).toBeDisabled();
	await dialog.getByLabel("Nota").fill("El proveedor envió 80");
	await dialog.getByRole("button", { name: "Ajustar" }).click();
	await expect(page.getByTestId("discrepancies").getByRole("row").filter({ hasText: created.number })).toHaveCount(0);
	await page.goto(`/#/invoices/${created.id}`);
	await expect(page.getByTestId("invoice-header")).toContainText("En asignación");
});
