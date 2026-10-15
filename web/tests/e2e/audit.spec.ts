// F1-17 · e2e: the audit log screen shows every write, and offers no way to change a record.
import { test, expect } from "../support/test";
import { e2eAdmin, newUser, signInAs } from "../support/api";


test("an administrator finds the creation of a user in the audit log", async ({ page }) => {
	const user = await newUser(["OFFICE"]);
	await signInAs(page, e2eAdmin());
	await page.goto("/#/audit");
	await page.getByPlaceholder("Buscar...").last().fill(user.id);
	// The search waits for typing to stop, so the row is found by the user's id too, not by its position.
	const created = page.getByRole("row").filter({ has: page.getByRole("cell", { name: "Usuario creado" }), hasText: user.id });
	await expect(created).toBeVisible();
	await expect(created).toContainText("e2e-admin");
	await expect(page.getByRole("button", { name: "Eliminar" })).toHaveCount(0);
	await expect(page.getByRole("button", { name: "Editar" })).toHaveCount(0);
});
