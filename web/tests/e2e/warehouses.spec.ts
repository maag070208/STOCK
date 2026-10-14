// F2-11 · e2e: the warehouses screens. An administrator creates a warehouse and a rack from the form,
// the groups by aisle and rack show it, and a duplicate rack shows the API's refusal next to the form.
import { test, expect } from "@playwright/test";
import { adminToken, call, e2eAdmin, newUser, signInAs, uniqueName } from "../support/api";

test("an administrator creates a warehouse and a rack, and the groups show the rack", async ({ page }) => {
	const code = uniqueName("W-").toUpperCase();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/warehouses");
	await page.getByRole("button", { name: "Nuevo almacén" }).click();
	let dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Código/).fill(code);
	await dialog.getByLabel(/^Nombre/).fill("Almacén de prueba");
	await dialog.getByRole("button", { name: "Guardar" }).click();

	// The new warehouse opens, with its temporary location and no racks yet.
	await expect(page.getByRole("heading", { name: `${code} · Almacén de prueba` })).toBeVisible();
	await expect(page.getByText("Este almacén aún no tiene racks.")).toBeVisible();
	await expect(page.getByTestId("locations").getByText("TEMPORARY · Temporal")).toBeVisible();

	await page.getByRole("button", { name: "Nuevo rack" }).click();
	dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Pasillo/).fill("k");
	await dialog.getByLabel(/^Rack/).fill("05");
	await dialog.getByLabel(/^Niveles/).fill("3");
	// The form shows the codes before it is sent.
	await expect(dialog.getByTestId("rack-preview")).toHaveText("Se crearán: K05A, K05B, K05C");
	await dialog.getByRole("button", { name: "Crear" }).click();
	await expect(dialog).toHaveCount(0);

	const rack = page.getByRole("button", { name: "Rack K05: 3 ubicaciones" });
	await expect(rack).toBeVisible();
	await rack.click();
	const rows = page.getByTestId("locations").getByRole("row");
	await expect(rows.filter({ hasText: /K05[ABC]/ })).toHaveCount(3);
	await expect(rows.filter({ hasText: "TEMPORARY" })).toHaveCount(0);
});

test("a rack that exists is refused in full, and the form names the codes that exist", async ({ page }) => {
	const token = await adminToken();
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de prueba" } })).json as { id: string };
	await call("POST", "/locations/racks", { token, body: { warehouseId: warehouse.id, aisle: "K", rack: "05", levels: 2 } });

	await signInAs(page, e2eAdmin());
	await page.goto(`/#/warehouses/${warehouse.id}`);
	await page.getByRole("button", { name: "Nuevo rack" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Pasillo/).fill("K");
	await dialog.getByLabel(/^Rack/).fill("05");
	await dialog.getByLabel(/^Niveles/).fill("4");
	await dialog.getByRole("button", { name: "Crear" }).click();
	await expect(dialog.getByText("Ya existen: K05A, K05B. No se creó ninguna ubicación.")).toBeVisible();
	await dialog.getByRole("button", { name: "Cancelar" }).click();
	await expect(page.getByRole("button", { name: "Rack K05: 2 ubicaciones" })).toBeVisible();
});

test("a person without locations view has no warehouses in the menu and cannot open the screen", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/");
	await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
	await expect(page.getByText("Almacenes", { exact: true })).toHaveCount(0);
	await page.goto("/#/warehouses");
	await expect(page.getByTestId("forbidden")).toBeVisible();
});

test("a warehouse manager sees the warehouses but cannot create them or their racks", async ({ page }) => {
	const token = await adminToken();
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén asignado" } })).json as { id: string };
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	await call("PUT", `/users/${manager.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
	await signInAs(page, manager);
	await page.goto("/#/warehouses");
	await expect(page.getByRole("cell", { name: "Almacén asignado" })).toBeVisible();
	await expect(page.getByRole("button", { name: "Nuevo almacén" })).toHaveCount(0);
	await page.goto(`/#/warehouses/${warehouse.id}`);
	await expect(page.getByRole("heading", { name: /Almacén asignado/ })).toBeVisible();
	await expect(page.getByRole("button", { name: "Nuevo rack" })).toHaveCount(0);
});
