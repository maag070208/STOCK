// F5-16 and F5-17 · e2e: office creates an order with two lines against the availability the warehouse has, assigns it
// to an operator and sees it in the list; then the actions of an order — validating it, moving picked material to
// another order, cancelling one with nothing picked, and downloading the receipt of a validated one, which is locked.
import { test, expect, type Page } from "../support/test";
import { newUser, signInAs, uniqueName } from "../support/api";
import { ordersFixture } from "../support/orders";
import { pickRemoteOption, pickSearchOption } from "../support/forms";

async function pickProduct(page: Page, index: number, product: { code: string; name: string }) {
	// The material of a line: the picker is reached by the name the screen gives it, one per line.
	await page.locator(`input[name="product-${index}"]`).fill(product.code);
	await page.getByText(`${product.code} · ${product.name}`).first().click();
}

test("office creates an order with two lines, assigns it and sees it in the list", async ({ page, browser }) => {
	const f = await ordersFixture();
	const [operator] = f.operators;
	const screws = await f.product("Tornillo de orden");
	const nails = await f.product("Clavo de orden");
	await f.stock(screws.id, 100);
	await f.stock(nails.id, 50, 1);

	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/orders");
	await page.getByRole("button", { name: "Nueva orden" }).click();

	const number = uniqueName("ORD-").toUpperCase();
	await page.getByLabel(/^Número de orden/).fill(number);
	await pickRemoteOption(page, "customerId", f.customer.code, f.customer.code);
	await pickRemoteOption(page, "warehouseId", f.warehouse.code, f.warehouse.code);

	// The first line shows what the warehouse has, and a quantity above it never reaches the API.
	await page.getByRole("button", { name: "Agregar material" }).click();
	await pickProduct(page, 0, screws);
	await expect(page.getByText("Disponible: 100")).toBeVisible();
	await page.locator('input[name="quantity-0"]').fill("150");
	await page.getByRole("button", { name: "Guardar orden" }).click();
	await expect(page.getByText("No hay esa cantidad disponible en el almacén.")).toBeVisible();
	await expect(page.getByRole("heading", { name: "Nueva orden" })).toBeVisible();
	await page.locator('input[name="quantity-0"]').fill("60");

	// The second line, with its own availability.
	await page.getByRole("button", { name: "Agregar material" }).click();
	await pickProduct(page, 1, nails);
	await expect(page.getByText("Disponible: 50")).toBeVisible();
	await page.locator('input[name="quantity-1"]').fill("20");
	await page.getByRole("button", { name: "Guardar orden" }).click();

	// The order was created with its two lines, and is assigned from its detail: the state is its badge, and the lines
	// are the rows of the table.
	await expect(page.getByText("Creada", { exact: true })).toBeVisible();
	await expect(page.getByRole("row").filter({ hasText: screws.code })).toContainText("60");
	await expect(page.getByRole("row").filter({ hasText: nails.code })).toContainText("20");
	await page.getByRole("button", { name: "Asignar" }).click();
	const dialog = page.getByRole("dialog");
	await pickSearchOption(page, "operatorId", operator.name, operator.name);
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(page.getByText(operator.name).first()).toBeVisible();

	// The list has it, and so it can be found by its number.
	await page.goto("/#/orders");
	await page.getByRole("columnheader", { name: /Número/ }).getByPlaceholder("Buscar...").fill(number);
	await expect(page.getByRole("row").filter({ hasText: number })).toContainText(operator.name);

	// Someone who cannot create orders is not offered the button.
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	const other = await browser.newPage();
	await signInAs(other, manager);
	await other.goto("/#/orders");
	await expect(other.getByRole("heading", { name: "Órdenes" })).toBeVisible();
	await expect(other.getByRole("button", { name: "Nueva orden" })).toHaveCount(0);
	await other.close();
});

test("a validated order keeps only its receipt, and picked material moves or the order is cancelled", async ({ page }) => {
	const f = await ordersFixture();
	const [operator] = f.operators;
	const screws = await f.product("Codo de orden");
	const nails = await f.product("Tuerca de orden");
	await f.stock(screws.id, 100);
	await f.stock(nails.id, 60);

	// An order picked in full: it can be validated, and then it is locked.
	const closed = await f.order({ number: uniqueName("ORD-").toUpperCase(), productId: screws.id, quantity: 10, operatorId: operator.id });
	const closedLine = closed.lines![0];
	await f.pick(closed.id, closedLine.id, f.locations[0].id, 10, operator);

	// An order with 20 of its 30 picked, and another open order that needs the same material.
	const source = await f.order({ number: uniqueName("ORD-").toUpperCase(), productId: screws.id, quantity: 30, operatorId: operator.id });
	const sourceLine = source.lines![0];
	await f.pick(source.id, sourceLine.id, f.locations[0].id, 20, operator);
	const target = await f.order({ number: uniqueName("ORD-").toUpperCase(), productId: screws.id, quantity: 20, operatorId: operator.id });

	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);

	// Validating: the order is checked, its receipt is stored, and every other action disappears.
	await page.goto(`/#/orders/${closed.id}`);
	await expect(page.getByText("Surtida", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: "Validar" }).click();
	await page.getByRole("dialog").getByRole("button", { name: "Validar" }).click();
	await expect(page.getByText("Validada", { exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "Validar" })).toHaveCount(0);
	await expect(page.getByRole("button", { name: "Cancelar orden" })).toHaveCount(0);
	await expect(page.getByRole("button", { name: "Pasar a otra orden" })).toHaveCount(0);
	await expect(page.getByRole("button", { name: "Descargar comprobante" })).toBeVisible();

	// The receipt is a private file: it downloads with the order's number, and it is a PDF.
	const download = page.waitForEvent("download");
	await page.getByRole("button", { name: "Descargar comprobante" }).click();
	const file = await download;
	expect(file.suggestedFilename()).toBe(`comprobante-${closed.number}.pdf`);
	const path = await file.path();
	expect(path).not.toBeNull();
	const { readFileSync } = await import("node:fs");
	expect(readFileSync(path!).subarray(0, 5).toString("latin1")).toBe("%PDF-");

	// Moving what was picked to the other order: no balance changes, and the target now has it.
	await page.goto(`/#/orders/${source.id}`);
	await page.getByRole("button", { name: "Pasar a otra orden" }).click();
	const transfer = page.getByRole("dialog");
	await pickSearchOption(page, "lineId", sourceLine.product.code, sourceLine.product.code);
	await pickSearchOption(page, "targetOrderId", target.number, target.number);
	await transfer.locator('input[name="quantity"]').fill("20");
	await transfer.locator('textarea[name="note"]').fill("El cliente cambió de orden");
	await transfer.getByRole("button", { name: "Pasar" }).click();
	// The detail reloads with the picked material gone from the source line, and then the API tells where it went.
	// The picked quantity is the third column of the line.
	await expect(page.getByRole("row").filter({ hasText: screws.code }).getByRole("cell").nth(2)).toHaveText("0");
	expect((await f.view(source.id)).lines![0].pickedQuantity).toBe(0);
	expect((await f.view(target.id)).lines![0].pickedQuantity).toBe(20);

	// With nothing picked, the source order can be cancelled; cancelling releases its reservation.
	await page.getByRole("button", { name: "Cancelar orden" }).click();
	await page.getByRole("dialog").getByRole("button", { name: "Cancelar orden" }).click();
	await expect(page.getByText("Cancelada", { exact: true })).toBeVisible();
	expect((await f.view(source.id)).status.code).toBe("CANCELLED");
});
