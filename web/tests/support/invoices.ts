// The support of the invoice screens: what the tests need to capture a fast invoice without depending on the order in
// which the API answers.
import { expect, type Page } from "@playwright/test";

// The modal of a new invoice asks for its four fields: the supplier is searched by its code, and the warehouse, which is
// a short list, is waited for before it is chosen.
export async function fillNewInvoice(page: Page, values: { number: string; supplier: { code: string; name: string }; warehouseId: string }) {
	// The number field, by its name: the label carries the required mark, so its text is not the whole name.
	await page.locator('input[name="number"]').fill(values.number);
	await page.locator('[name="supplierId"]').fill(values.supplier.code);
	await page.locator("div.max-h-60").last().getByText(`${values.supplier.code} · ${values.supplier.name}`).click();
	await expect(page.locator(`select[name="warehouseId"] option[value="${values.warehouseId}"]`)).toHaveCount(1);
	await page.locator('select[name="warehouseId"]').selectOption(values.warehouseId);
}

// Captures a material in the detail's quick bar: the picker by its code, and the quantity with the keyboard. The options
// of the picker are a floating panel outside the page, and the table of the invoice shows the same text once the material
// is already a line, so the panel is what is read.
export async function captureMaterial(page: Page, product: { code: string; name: string }, quantity: string) {
	const bar = page.getByTestId("quick-capture");
	await bar.getByPlaceholder("Busca por código o nombre").fill(product.code);
	await page.locator("div.max-h-60").last().getByText(`${product.code} · ${product.name}`).click();
	await page.locator('input[name="capture-quantity"]').fill(quantity);
	await page.locator('input[name="capture-quantity"]').press("Enter");
}
