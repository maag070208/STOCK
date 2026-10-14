// F1-19 · e2e: the access console. An administrator changes a matrix cell and a policy;
// a person without roles.manage cannot open it.
import { test, expect } from "@playwright/test";
import { adminToken, call, e2eAdmin, newUser, signInAs } from "../support/api";


test.describe.configure({ mode: "serial" });

// The test works on a role of its own, so it never changes what other tests rely on.
test("an administrator changes a matrix cell, and the change is saved", async ({ page }) => {
	const role = "T_" + Date.now().toString(36).toUpperCase();
	const token = await adminToken();
	await call("POST", "/roles", { token, body: { key: role, name: "Rol de la prueba" } });
	try {
		await signInAs(page, e2eAdmin());
		await page.goto("/#/roles");
		await page.getByText("Matriz", { exact: true }).click();
		const cell = page.locator(`select[data-cell="${role}|audit.view"]`);
		await expect(cell).toHaveValue("NONE");
		await cell.selectOption("ALL");
		await expect(page.getByText("1 cambios sin guardar")).toBeVisible();
		await page.getByRole("button", { name: "Guardar cambios" }).click();
		await expect(page.getByText("Matriz guardada.")).toBeVisible();
		const matrix = await call("GET", "/roles/matrix", { token });
		expect(matrix.json).toContainEqual({ roleKey: role, permissionKey: "audit.view", scope: "ALL" });
		// "What each role sees" follows the matrix.
		await page.getByText("Qué ve cada rol", { exact: true }).click();
		await expect(page.locator(`[data-screen="audit|${role}"]`)).toHaveText("✓");
		await expect(page.locator(`[data-screen="users|${role}"]`)).toHaveText("—");
	} finally {
		await call("DELETE", `/roles/${role}`, { token });
	}
});

test("the console refuses a change that would leave nobody able to manage access", async ({ page }) => {
	await signInAs(page, e2eAdmin());
	await page.goto("/#/roles");
	await page.getByText("Matriz", { exact: true }).click();
	await page.locator('select[data-cell="ADMIN|roles.manage"]').selectOption("NONE");
	await page.getByRole("button", { name: "Guardar cambios" }).click();
	await expect(page.getByText("El cambio dejaría el sistema sin un administrador activo.")).toBeVisible();
});

test("an administrator creates a policy with a condition, and deletes it", async ({ page }) => {
	const key = "P_WEB_" + Date.now().toString(36).toUpperCase();
	// Whatever happens, the policy does not outlive the test.
	test.info().annotations.push({ type: "cleanup", description: key });
	page.on("close", () => void adminToken().then((token) => call("DELETE", `/policies/${key}`, { token })));
	await signInAs(page, e2eAdmin());
	await page.goto("/#/roles");
	await page.getByText("Políticas", { exact: true }).click();
	await page.getByRole("button", { name: "Nueva política" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Clave/).fill(key);
	await dialog.getByRole("button", { name: "Agregar condición" }).click();
	// The condition names a person who does not exist, so the policy never touches what other tests do.
	const nobody = crypto.randomUUID();
	await dialog.locator('select[name="field-0"]').selectOption("target.id");
	await dialog.locator('select[name="operator-0"]').selectOption("eq");
	await dialog.getByLabel("Valor", { exact: true }).fill(nobody);
	await dialog.getByRole("button", { name: "Guardar" }).click();

	const row = page.getByTestId("policies-table").getByRole("row").filter({ hasText: key });
	await expect(row).toBeVisible();
	await expect(row).toContainText(`target.id es igual a ${nobody}`);
	const policies = await call("GET", "/policies", { token: await adminToken() });
	expect(policies.json.find((p: { key: string }) => p.key === key).conditions).toEqual([{ field: "target.id", operator: "eq", value: nobody }]);

	await row.getByRole("button", { name: "Eliminar" }).click();
	// The kit's confirmation opens last, so its button is the last one with that name.
	await page.getByRole("button", { name: "Eliminar" }).last().click();
	await expect(row).toHaveCount(0);
});

test("a person without roles.manage cannot open the console", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/roles");
	await expect(page.getByTestId("forbidden")).toBeVisible();
	await expect(page.getByRole("tab", { name: "Matriz" })).toHaveCount(0);
});

test("what each role sees comes from the screens and the matrix", async ({ page }) => {
	await signInAs(page, e2eAdmin());
	await page.goto("/#/roles");
	await page.getByText("Qué ve cada rol", { exact: true }).click();
	await expect(page.locator('[data-screen="audit|ADMIN"]')).toHaveText("✓");
	await expect(page.locator('[data-screen="catalogs|OFFICE"]')).toHaveText("✓");
	await expect(page.locator('[data-screen="users|OFFICE"]')).toHaveText("—");
	await expect(page.locator('[data-screen="home|APP_USER"]')).toHaveText("✓");
});
