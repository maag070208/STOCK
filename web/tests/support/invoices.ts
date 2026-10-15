// The support of the invoice screens: what the tests need to capture a fast invoice without depending on the order in
// which the API answers.
import { expect, type Page } from "@playwright/test";
import { pickRemoteOption } from "./forms";

// The modal of a new invoice asks for its four fields, and the two lists it carries (supplier and warehouse) are
// searched, because an installation holds hundreds of each.
export async function fillNewInvoice(page: Page, values: { number: string; supplier: { code: string; name: string }; warehouse: { id: string; code: string } }) {
	// The number field, by its name: the label carries the required mark, so its text is not the whole name.
	await page.locator('input[name="number"]').fill(values.number);
	await pickRemoteOption(page, "supplierId", values.supplier.code, `${values.supplier.code} · ${values.supplier.name}`);
	await pickRemoteOption(page, "warehouseId", values.warehouse.code, values.warehouse.code);
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
