// F1-15 and F1-16 · e2e: the catalogs screen. Who can read sees the values; who can manage adds and deactivates them.
import { test, expect } from "../support/test";
import { e2eAdmin, newUser, signInAs, uniqueName } from "../support/api";
import { pickSearchOption } from "../support/forms";


test("the seed catalogs are there, read-only for an office user", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/catalogs");
	await pickSearchOption(page, "catalog", "INVOICE_STATUS", /INVOICE_STATUS/);
	const items = page.getByRole("table");
	await expect(items.getByText("Abierta")).toBeVisible();
	await expect(items.getByText("Cerrada")).toBeVisible();
	await expect(page.getByRole("button", { name: "Nuevo valor" })).toHaveCount(0);
	await expect(items.getByRole("button", { name: "Editar" })).toHaveCount(0);
});

test("an administrator creates a catalog and a value, and a deactivated value hides until asked for", async ({ page }) => {
	const code = uniqueName("TEST_").toUpperCase();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/catalogs");
	await page.getByRole("button", { name: "Nuevo catálogo" }).click();
	await page.getByRole("dialog").getByLabel(/^Código/).fill(code);
	await page.getByRole("dialog").getByRole("button", { name: "Crear" }).click();
	await expect(page.locator('input[name="catalog"]')).toHaveValue(new RegExp(code));

	await page.getByRole("button", { name: "Nuevo valor" }).click();
	let dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Código/).fill("KG");
	await dialog.getByLabel("Nombre en español", { exact: true }).fill("Kilogramo");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	// An active value needs both names: the API says which one is missing.
	await expect(dialog.getByText("Este campo es obligatorio.")).toBeVisible();
	await dialog.getByLabel("Nombre en inglés", { exact: true }).fill("Kilogram");
	await dialog.getByLabel("Decimales permitidos", { exact: true }).fill("3");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	const row = page.getByRole("row").filter({ hasText: "KG" });
	await expect(row).toContainText("Kilogramo");
	await expect(row).toContainText("Decimales: 3");

	await row.getByRole("button", { name: "Editar" }).click();
	dialog = page.getByRole("dialog");
	await expect(dialog.getByLabel(/^Código/)).toBeDisabled();
	// The kit's checkbox keeps its input hidden: the label around it is what a person clicks.
	await dialog.locator('label:has(input[name="active"])').click();
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(page.getByRole("row").filter({ hasText: "KG" })).toHaveCount(0);
	await page.getByText("Mostrar inactivos").click();
	await expect(row).toContainText("Inactivo");
});
