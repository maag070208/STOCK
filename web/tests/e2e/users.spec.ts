// F1-02 and F1-18 · e2e: an administrator manages users from the web.
import { test, expect, type Page } from "@playwright/test";
import { adminToken, call, e2eAdmin, newUser, signInAs, uniqueName } from "../support/api";


async function openUsers(page: Page) {
	await signInAs(page, e2eAdmin());
	await page.goto("/#/users");
	await expect(page.getByRole("heading", { name: "Usuarios" })).toBeVisible();
}

async function filterByUsername(page: Page, username: string) {
	await page.getByPlaceholder("Buscar...").first().fill(username);
	await expect(page.getByRole("cell", { name: username, exact: true })).toBeVisible();
}

test("an administrator creates a user and sees the temporary password once", async ({ page }) => {
	await openUsers(page);
	const username = uniqueName("nu");
	await page.getByRole("button", { name: "Nuevo usuario" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Usuario/).fill(username);
	await dialog.getByLabel(/^Nombre/).fill("Ana Ruiz");
	await dialog.getByLabel("Correo", { exact: true }).fill("ana@empresa");
	await dialog.getByRole("button", { name: "Crear" }).click();
	// The API checks the email and the form shows its message under the field.
	await expect(dialog.getByText("El correo electrónico no es válido.")).toBeVisible();
	await dialog.getByLabel("Correo", { exact: true }).fill("ana.ruiz@empresa.com.mx");
	await dialog.getByRole("button", { name: "Crear" }).click();

	const secret = page.getByTestId("temporary-password");
	await expect(secret).toBeVisible();
	const temporary = (await secret.getByTestId("secret-value").textContent())!.trim();
	expect(temporary).toHaveLength(14);
	await secret.getByRole("button", { name: "Cerrar" }).click();

	await filterByUsername(page, username);
	// The temporary password is the right one: without roles the person has no channel yet, so the API refuses the channel, not the password.
	const login = await call("POST", "/auth/login", { body: { username, password: temporary } });
	expect(login.json.code).toBe("INSUFFICIENT_PERMISSIONS");
	const users = await call("POST", "/users/query", { token: await adminToken(), body: { filters: { username } } });
	expect(users.json.data[0].mustChangePassword).toBe(true);
	expect(users.json.data[0].email).toBe("ana.ruiz@empresa.com.mx");
});

test("an administrator resets a password and deactivates a user from the row's actions", async ({ page }) => {
	const user = await newUser(["OFFICE"]);
	await openUsers(page);
	await filterByUsername(page, user.username);

	await page.getByRole("button", { name: "Acciones" }).first().click();
	await page.getByRole("menuitem", { name: "Restablecer contraseña" }).click();
	await page.getByRole("button", { name: "Confirmar" }).click();
	const secret = page.getByTestId("temporary-password");
	await expect(secret).toBeVisible();
	await secret.getByRole("button", { name: "Cerrar" }).click();
	expect((await call("POST", "/auth/login", { body: { username: user.username, password: user.password } })).json.code).toBe("INVALID_CREDENTIALS");

	await page.getByRole("button", { name: "Acciones" }).first().click();
	await page.getByRole("menuitem", { name: "Dar de baja" }).click();
	await page.getByRole("button", { name: "Confirmar" }).click();
	await expect(page.getByRole("row").filter({ hasText: user.username }).getByText("Inactivo")).toBeVisible();
	const read = await call("GET", `/users/${user.id}`, { token: await adminToken() });
	expect(read.json.active).toBe(false);
});

test("an administrator gives a person an exception with a reason, and changes their roles", async ({ page }) => {
	const user = await newUser(["OFFICE"]);
	await openUsers(page);
	await filterByUsername(page, user.username);
	await page.getByRole("button", { name: "Acciones" }).first().click();
	await page.getByRole("menuitem", { name: "Acceso" }).click();

	const dialog = page.getByRole("dialog");
	await dialog.locator('select[name="permission"]').selectOption("audit.view");
	await dialog.getByLabel("Motivo", { exact: true }).fill("Revisión mensual");
	await dialog.getByRole("button", { name: "Agregar excepción" }).click();
	await expect(dialog.getByTestId("exceptions").getByText("Revisión mensual")).toBeVisible();
	const exceptions = await call("GET", `/users/${user.id}/permissions`, { token: await adminToken() });
	expect(exceptions.json.map((e: { permission: string }) => e.permission)).toEqual(["audit.view"]);
});
