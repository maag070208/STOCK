// F1-18 · e2e: sign-in, the forced password change, the menu by permissions, the 403 page and sign-out.
import { test, expect, type Page } from "../support/test";
import { newUser, signInAs, skipIntro } from "../support/api";

const menu = (page: Page) => page.locator("aside nav").first();

async function signInThroughTheForm(page: Page, username: string, password: string) {
	await page.getByLabel("Usuario", { exact: true }).fill(username);
	await page.getByLabel("Contraseña", { exact: true }).fill(password);
	await page.getByRole("button", { name: "Entrar" }).click();
}

test("a new user signs in, changes the temporary password, and reaches the menu", async ({ page }) => {
	const user = await newUser(["OFFICE"], { pendingChange: true });
	await skipIntro(page);
	await page.goto("/#/login");
	await signInThroughTheForm(page, user.username, user.password);

	await expect(page).toHaveURL(/#\/change-password$/);
	await expect(page.getByText("Tu contraseña es temporal")).toBeVisible();
	// Nothing else opens while the change is pending.
	await page.goto("/#/catalogs");
	await expect(page).toHaveURL(/#\/change-password$/);

	await page.getByLabel("Contraseña actual", { exact: true }).fill(user.password);
	await page.getByLabel("Contraseña nueva", { exact: true }).fill(user.newPassword);
	await page.getByLabel("Confirma la contraseña nueva", { exact: true }).fill(user.newPassword);
	await page.getByRole("button", { name: "Guardar contraseña" }).click();

	await expect(page).toHaveURL(/#\/$/);
	await expect(page.getByText(`Hola, ${user.name}`)).toBeVisible();
	await expect(menu(page).getByRole("button", { name: "Inicio" })).toBeVisible();
});

test("the password change asks for matching copies, and shows the API's refusal by field", async ({ page }) => {
	const user = await newUser(["OFFICE"], { pendingChange: true });
	await signInAs(page, user);
	await page.goto("/#/change-password");
	await page.getByLabel("Contraseña actual", { exact: true }).fill(user.password);
	await page.getByLabel("Contraseña nueva", { exact: true }).fill("Una-Clave-Nueva-1");
	await page.getByLabel("Confirma la contraseña nueva", { exact: true }).fill("Otra-Clave-Nueva-2");
	await page.getByRole("button", { name: "Guardar contraseña" }).click();
	await expect(page.getByText("Las contraseñas no coinciden.")).toBeVisible();

	await page.getByLabel("Contraseña nueva", { exact: true }).fill("cort");
	await page.getByLabel("Confirma la contraseña nueva", { exact: true }).fill("cort");
	await page.getByRole("button", { name: "Guardar contraseña" }).click();
	await expect(page.getByText("La contraseña debe tener entre 5 y 72 caracteres.")).toBeVisible();
});

test("the menu shows only the screens the person's permissions allow", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/");
	await expect(menu(page).getByRole("button", { name: "Catálogos" })).toBeVisible();
	for (const hidden of ["Usuarios", "Acceso", "Bitácora"]) await expect(menu(page).getByRole("button", { name: hidden })).toHaveCount(0);
});

test("a screen without the permission shows the 403 page", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/roles");
	await expect(page.getByTestId("forbidden")).toBeVisible();
	await expect(page.getByText("No tienes permiso para ver esta pantalla.")).toBeVisible();
});

test("a wrong password, and a user without web access, see the API's message", async ({ page }) => {
	const appUser = await newUser(["APP_USER"], { channel: "app" });
	await skipIntro(page);
	await page.goto("/#/login");
	await signInThroughTheForm(page, appUser.username, "Clave-incorrecta-1");
	await expect(page.getByText("El usuario o la contraseña no son correctos.")).toBeVisible();
	await signInThroughTheForm(page, appUser.username, appUser.password);
	await expect(page.getByText("No tienes permiso para realizar esta acción.")).toBeVisible();
	await expect(page).toHaveURL(/#\/login$/);
});

test("empty fields are required before anything is sent", async ({ page }) => {
	await skipIntro(page);
	await page.goto("/#/login");
	await page.getByRole("button", { name: "Entrar" }).click();
	await expect(page.getByText("Este campo es obligatorio.")).toHaveCount(2);
});

test("the session survives a reload, and signing out ends it", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await skipIntro(page);
	await page.goto("/#/login");
	await signInThroughTheForm(page, office.username, office.password);
	await expect(page.getByText(`Hola, ${office.name}`)).toBeVisible();
	await page.reload();
	await expect(page.getByText(`Hola, ${office.name}`)).toBeVisible();

	await page.getByRole("banner").getByRole("button", { name: new RegExp(office.name) }).click();
	await page.getByText("Cerrar sesión").click();
	await expect(page).toHaveURL(/#\/login$/);
	await page.goto("/#/");
	await expect(page).toHaveURL(/#\/login$/);
});

test("the language chosen in the top bar is saved on the profile", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	await page.goto("/#/");
	// The screen changes at once; the profile is saved in the background, and the reload waits for it.
	const saved = page.waitForResponse((response) => response.url().endsWith("/auth/me") && response.request().method() === "PATCH");
	await page.getByRole("banner").getByRole("combobox", { name: "Idioma" }).selectOption("en");
	await expect(page.getByText(`Hello, ${office.name}`)).toBeVisible();
	expect((await saved).status()).toBe(200);
	// A new browser session reads the language from the profile.
	await page.evaluate(() => window.localStorage.removeItem("stockflow.locale"));
	await page.reload();
	await expect(page.getByText(`Hello, ${office.name}`)).toBeVisible();
});
