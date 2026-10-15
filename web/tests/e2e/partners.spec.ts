// F2-13 · e2e: the suppliers and customers screens. An administrator creates a supplier with a contact; a wrong RFC
// shows the API's error next to its field; an office user sees the lists and cannot open the form.
import { test, expect } from "@playwright/test";
import { e2eAdmin, newUser, signInAs, testPaymentTerms, uniqueName } from "../support/api";
import { pickSearchOption } from "../support/forms";

test("an administrator creates a supplier, sees a wrong RFC next to its field, and adds a contact", async ({ page }) => {
	const terms = await testPaymentTerms();
	const code = uniqueName("PR-").toUpperCase();
	await signInAs(page, e2eAdmin());
	await page.goto("/#/suppliers");
	await page.getByRole("button", { name: "Nuevo proveedor" }).click();
	const form = page.getByRole("form", { name: "Nuevo proveedor" });
	await form.getByLabel(/^Código\s*\*?$/).fill(code);
	await form.getByLabel(/^RFC/).fill("ADN011303AB1");
	await form.getByLabel(/^Nombre o razón social/).fill("Aceros del Norte");
	await pickSearchOption(page, "paymentTerms", terms.label, terms.label);
	await form.getByLabel(/^Calle/).fill("Av. Juárez");
	await form.getByLabel(/^Número exterior/).fill("120");
	await form.getByLabel(/^Colonia/).fill("Centro");
	await form.getByLabel(/^Ciudad/).fill("Guadalajara");
	await form.getByLabel(/^Código postal/).fill("44100");
	await pickSearchOption(page, "state", "Jalisco", /Jalisco/);
	await form.getByRole("button", { name: "Guardar" }).click();
	// The API's message, in Spanish, under the RFC.
	await expect(form.getByText("El RFC no es válido", { exact: false })).toBeVisible();

	await form.getByLabel(/^RFC/).fill("ADN010203AB1");
	await form.getByRole("button", { name: "Guardar" }).click();
	await expect(page.getByRole("heading", { name: `${code} · Aceros del Norte` })).toBeVisible();
	await expect(page.getByText("Jalisco", { exact: false }).first()).toBeVisible();
	await expect(page.getByText(terms.label, { exact: false }).first()).toBeVisible();

	await page.getByRole("button", { name: "Nuevo contacto" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Nombre/).fill("Ana Pérez");
	// Without an email or a phone the contact cannot be saved.
	await expect(dialog.getByRole("button", { name: "Guardar" })).toBeDisabled();
	await dialog.getByLabel(/^Teléfono/).fill("(33) 1234-5678");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	const contact = page.getByRole("row").filter({ hasText: "Ana Pérez" });
	await expect(contact).toContainText("3312345678");
});

test("an office user sees the suppliers and customers, and cannot open their forms", async ({ page }) => {
	const office = await newUser(["OFFICE"]);
	await signInAs(page, office);
	for (const [path, title, button] of [["suppliers", "Proveedores", "Nuevo proveedor"], ["customers", "Clientes", "Nuevo cliente"]]) {
		await page.goto(`/#/${path}`);
		await expect(page.getByRole("heading", { name: title })).toBeVisible();
		await expect(page.getByRole("button", { name: button })).toHaveCount(0);
		await page.goto(`/#/${path}/new`);
		await expect(page.getByTestId("forbidden")).toBeVisible();
	}
});
