// F4-19 · e2e: the stock screens. The opening inventory loads from its screen; the stock lists show the balances with
// their unit's decimals, and follow the reader's warehouses.
import { test, expect } from "../support/test";
import { adminToken, call, e2eAdmin, newUser, signInAs, uniqueName, upload } from "../support/api";
import { legacyInventory } from "../support/xlsx";

// This test's own materials, imported as materials (F2), and a warehouse of its own.
async function setup() {
	const token = await adminToken();
	const prefix = uniqueName("T").toUpperCase();
	const firstId = 2_000_000 + Math.floor(Math.random() * 1_000_000_000);
	const products = [
		{ id: firstId, code: `${prefix}0`, name: "Tornillo de prueba", stock: 1500 },
		{ id: firstId + 1, code: `${prefix}1`, name: "Clavo de prueba", stock: 25 },
	];
	const file = legacyInventory(products);
	const review = await upload("/products/import/dry-run", token, file);
	await upload("/products/import", token, file, { checksum: review.json.checksum });
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de prueba" } })).json as { id: string; code: string };
	return { token, file, products, warehouse };
}

test("an administrator loads the opening inventory from its screen, and the stock shows it in the temporary location", async ({ page }) => {
	const { file, products, warehouse } = await setup();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/stock");
	await page.getByRole("button", { name: "Inventario inicial" }).click();
	// The warehouse is searched by its code: an installation holds hundreds of them (the picker asks the API).
	await page.locator('input[name="warehouseId"]').fill(warehouse.code);
	await page.locator("div.max-h-60").last().getByText(warehouse.code, { exact: false }).first().click();
	await page.locator('input[name="file"]').setInputFiles({ name: "inventario.xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buffer: file });
	await page.getByRole("button", { name: "Revisar archivo" }).click();
	const report = page.getByTestId("opening-report");
	await expect(report.getByText("La suma (1,525) coincide con el total de control (1,525).")).toBeVisible();
	await page.getByRole("button", { name: "Cargar 2 materiales con existencias" }).click();
	await expect(page.getByText("Se cargaron 2 materiales en la ubicación temporal.")).toBeVisible();

	await page.goto("/#/stock");
	const byLocation = page.getByTestId("stock-by-location");
	await byLocation.getByRole("columnheader", { name: /Material/ }).getByPlaceholder("Buscar...").fill(products[0].code);
	const row = byLocation.getByRole("row").filter({ hasText: products[0].code });
	await expect(row).toContainText("TEMPORARY · Temporal");
	await expect(row).toContainText("1,500");
	await expect(row).toContainText(warehouse.code);
});

test("a reader with AREA sees only the stock of their warehouses, and without stock.view there is no screen", async ({ page, browser }) => {
	const { token, file, products, warehouse } = await setup();
	const review = await upload("/stock/opening/dry-run", token, file, { warehouseId: warehouse.id });
	await upload("/stock/opening", token, file, { warehouseId: warehouse.id, checksum: review.json.checksum });
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	await call("PUT", `/users/${manager.id}/permissions/stock.view`, { token, body: { scope: "AREA", reason: "Prueba de alcance" } });

	// Without the warehouse in their area, the manager sees none of its stock.
	await signInAs(page, manager);
	await page.goto("/#/stock");
	const byLocation = page.getByTestId("stock-by-location");
	await byLocation.getByRole("columnheader", { name: /Material/ }).getByPlaceholder("Buscar...").fill(products[0].code);
	await expect(byLocation.getByRole("row").filter({ hasText: products[0].code })).toHaveCount(0);
	await call("PUT", `/users/${manager.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
	await page.reload();
	await byLocation.getByRole("columnheader", { name: /Material/ }).getByPlaceholder("Buscar...").fill(products[0].code);
	await expect(byLocation.getByRole("row").filter({ hasText: products[0].code })).toHaveCount(1);
	await expect(page.getByRole("button", { name: "Inventario inicial" })).toHaveCount(0);

	// Another person, in a page of their own: a session is set when a page first loads. The office reads stock
	// everywhere since F5-18, so the refusal is shown to a role that has web access and nothing else.
	const role = uniqueName("T").toUpperCase();
	await call("POST", "/roles", { token, body: { key: role, name: "Rol de prueba" } });
	await call("PUT", "/roles/matrix", { token, body: { changes: [{ roleKey: role, permissionKey: "web.access", scope: "ALL" }] } });
	const nobody = await newUser([role]);
	const other = await browser.newPage();
	await signInAs(other, nobody);
	await other.goto("/#/stock");
	await expect(other.getByTestId("forbidden")).toBeVisible();
	await other.close();
});
