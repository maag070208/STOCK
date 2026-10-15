// F4-20, F4-21 and F8: office captures an invoice in the modal over the list, adds its materials in the quick bar and
// assigns them from the detail (the mock leaves the assignment out of the creation). Discrepancies are adjusted by who
// may resolve them, and only them.
import { test, expect, type Page } from "@playwright/test";
import { businessDate, call, newUser, signInAs, uniqueName } from "../support/api";
import { entriesFixture } from "../support/entries";
import { captureMaterial, fillNewInvoice } from "../support/invoices";

test("office creates an invoice in the modal and assigns its line from the detail", async ({ page }) => {
	const f = await entriesFixture();
	const [a, b] = f.operators;
	const screws = await f.product("Tornillo de prueba");
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/invoices");
	await page.getByRole("button", { name: "Nueva factura" }).click();

	// An inactive supplier is not offered for a new invoice: its code is searched and nothing matches.
	await page.locator('[name="supplierId"]').fill(f.inactiveSupplier.code);
	await expect(page.locator("div.max-h-60").getByText(f.inactiveSupplier.name)).toHaveCount(0);

	const number = uniqueName("FA-").toUpperCase();
	await fillNewInvoice(page, { number, supplier: f.supplier, warehouseId: f.warehouse.id });

	// The primary action lands on the detail, where the materials are captured.
	await page.getByRole("button", { name: "Guardar y agregar materiales" }).click();
	await expect(page.getByRole("heading", { name: new RegExp(number) })).toBeVisible();
	await captureMaterial(page, screws, "100");
	await expect(page.getByText("1 línea · 100 piezas")).toBeVisible();

	// The assignment from the line: a split that does not add up is blocked before it is sent.
	await page.getByRole("button", { name: "Asignar" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.locator('select[name="operator-0"]').selectOption(a.id);
	await dialog.locator('input[name="share-0"]').fill("60");
	await dialog.getByRole("button", { name: "Agregar operador" }).click();
	await dialog.locator('select[name="operator-1"]').selectOption(b.id);
	await dialog.locator('input[name="share-1"]').fill("30");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(dialog.getByText("Las cantidades deben sumar la cantidad de la línea.")).toBeVisible();
	await dialog.locator('input[name="share-1"]').fill("40");
	await dialog.getByRole("button", { name: "Guardar" }).click();

	// The invoice is pending, and each share is a row of the reception, with what its operator has to receive.
	await expect(page.getByText("Pendiente")).toBeVisible();
	await expect(page.getByRole("row", { name: new RegExp(a.name) })).toContainText("60");
	await expect(page.getByRole("row", { name: new RegExp(b.name) })).toContainText("40");
});

test("a discrepancy is adjusted by the warehouse's manager or by office, and not by someone without the permission", async ({ page, browser }) => {
	const f = await entriesFixture();
	const [a] = f.operators;
	const nails = await f.product("Clavo de prueba");
	const body = (number: string) => ({
		number, supplierId: f.supplier.id, invoiceDate: businessDate(), warehouseId: f.warehouse.id,
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
	await expect(readerPage.getByText(/Diferencia -20/)).toBeVisible();
	await expect(readerPage.getByRole("button", { name: "Ajustar" })).toHaveCount(0);
	await readerPage.goto("/#/discrepancies");
	await expect(readerPage.getByTestId("forbidden")).toBeVisible();
	await readerPage.close();

	// The manager of the warehouse adjusts it from the list, with a note.
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	await call("PUT", `/users/${manager.id}/warehouses`, { token: f.token, body: { warehouseIds: [f.warehouse.id] } });
	await signInAs(page, manager);
	await page.goto("/#/discrepancies");
	const row = page.getByRole("table").getByRole("row").filter({ hasText: created.number });
	await expect(row).toContainText("-20");
	await row.getByRole("button", { name: "Ajustar" }).click();
	const dialog = page.getByRole("dialog");
	await expect(dialog.getByRole("button", { name: "Ajustar" })).toBeDisabled();
	await dialog.getByLabel("Nota").fill("El proveedor envió 80");
	await dialog.getByRole("button", { name: "Ajustar" }).click();
	await expect(page.getByRole("table").getByRole("row").filter({ hasText: created.number })).toHaveCount(0);
	await page.goto(`/#/invoices/${created.id}`);
	await expect(page.getByText("En asignación")).toBeVisible();
	await expect(page.getByRole("row", { name: new RegExp(a.name) })).toContainText("80");
});
