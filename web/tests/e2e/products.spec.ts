// F2-12 · e2e: the materials screens and the import of the inventory file. The dry run shows its counts before the
// confirm is enabled, the confirm imports, and a second import of the same file finds nothing new.
import { test, expect } from "../support/test";
import { e2eAdmin, newUser, signInAs, uniqueName } from "../support/api";
import { legacyInventory } from "../support/xlsx";

// This test's own codes and legacy ids, far from the real ones (1 to 1,398) and from other tests'.
function fixture() {
	const prefix = uniqueName("T").toUpperCase();
	const firstId = 2_000_000 + Math.floor(Math.random() * 1_000_000_000);
	const products = [
		{ id: firstId, code: `${prefix}0`, name: "ABRAZADERA  DE ARRANQUE 1-5/8\"", stock: 10 },
		{ id: firstId + 1, code: `${prefix}1`, name: "ADORNO MALEABLE #10", stock: 20 },
		{ id: firstId + 2, code: `${prefix}2`, name: "VARILLA SINFIN GALV. 5/16\" X 1 MT", stock: 30 },
	];
	return { codes: products.map((p) => p.code), file: { name: "inventario.xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buffer: legacyInventory(products) } };
}

test("an administrator reviews the inventory file, imports it, and a second import finds nothing new", async ({ page }) => {
	const { codes, file } = fixture();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/products");
	await page.getByRole("button", { name: "Importar inventario" }).click();
	await expect(page.getByRole("heading", { name: "Importar inventario" })).toBeVisible();
	await page.locator('input[name="file"]').setInputFiles(file);
	await page.getByRole("button", { name: "Revisar archivo" }).click();

	await expect(page.getByText("Materiales nuevos", { exact: true })).toBeVisible();
	await expect(page.getByText("La suma de existencias (60) coincide con el total de control (60).")).toBeVisible();
	// The two rows of the note have no code: they are listed with the API's reason.
	await expect(page.getByRole("row").filter({ hasText: "Este campo es obligatorio." })).toHaveCount(2);
	await page.getByRole("button", { name: "Importar 3 materiales nuevos" }).click();
	await expect(page.getByText("Se importaron 3 materiales nuevos. 0 ya existían.")).toBeVisible();

	// The list has them, named as written, with doubled spaces collapsed, and each code as its barcode.
	await page.goto("/#/products");
	await page.getByRole("columnheader", { name: /Código$/ }).getByPlaceholder("Buscar...").fill(codes[0]);
	const row = page.getByRole("row").filter({ hasText: codes[0] });
	await expect(row).toContainText("ABRAZADERA DE ARRANQUE 1-5/8\"");
	await expect(row).toContainText("Pieza");

	// The same file again: the review finds them all, and the import creates nothing.
	await page.goto("/#/products/import");
	await page.locator('input[name="file"]').setInputFiles(file);
	await page.getByRole("button", { name: "Revisar archivo" }).click();
	await page.getByRole("button", { name: "Importar 0 materiales nuevos" }).click();
	await expect(page.getByText("No hay materiales nuevos: todos ya existían.")).toBeVisible();
});

test("an administrator creates a material, and its code is its barcode", async ({ page }) => {
	const code = uniqueName("M").toUpperCase();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/products");
	await page.getByRole("button", { name: "Nuevo material" }).click();
	const dialog = page.getByRole("dialog");
	// "Código" and "Código de barras" both start alike: the code field is matched whole.
	await dialog.getByLabel(/^Código\s*\*?$/).fill(code);
	await dialog.getByLabel(/^Nombre/).fill("Codo  90° galvanizado");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(dialog).toHaveCount(0);

	await page.getByRole("columnheader", { name: /Código$/ }).getByPlaceholder("Buscar...").fill(code);
	const row = page.getByRole("row").filter({ hasText: code });
	await expect(row).toContainText("Codo 90° galvanizado");
	await row.getByText("Codo 90° galvanizado").click();
	// The record is titled by its code, the strip says it is its own barcode, and the barcode draws it.
	await expect(page.getByRole("heading", { name: code })).toBeVisible();
	await expect(page.getByText("Código de barras", { exact: true })).toBeVisible();
	await expect(page.getByText(code, { exact: true })).toHaveCount(3);
});

test("an office user sees the materials, and cannot create them or open the import", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/products");
	await expect(page.getByRole("heading", { name: "Materiales" })).toBeVisible();
	await expect(page.getByRole("button", { name: "Nuevo material" })).toHaveCount(0);
	await expect(page.getByRole("button", { name: "Importar inventario" })).toHaveCount(0);
	await page.goto("/#/products/import");
	await expect(page.getByTestId("forbidden")).toBeVisible();
});
