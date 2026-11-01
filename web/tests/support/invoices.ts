// The support of the invoice screens: capture, receiving and placement through the same contracts as the clients.
import { expect, type Page } from "@playwright/test";
import { pickRemoteOption } from "./forms";
import { call, uniqueName, type TestUser } from "./api";

export async function fillNewInvoice(page: Page, values: { number: string; supplier: { code: string; name: string }; warehouse: { id: string; code: string } }) {
	await page.locator('input[name="number"]').fill(values.number);
	await pickRemoteOption(page, "supplierId", values.supplier.code, `${values.supplier.code} · ${values.supplier.name}`);
	await pickRemoteOption(page, "warehouseId", values.warehouse.code, values.warehouse.code);
}

export async function captureMaterial(page: Page, product: { code: string; name: string }, quantity: string) {
	const bar = page.getByTestId("quick-capture");
	await bar.getByPlaceholder("Escanea o busca por código, barras o nombre").fill(product.code);
	await page.locator("div.max-h-60").last().getByText(`${product.code} · ${product.name}`).click();
	await page.locator('input[name="capture-quantity"]').fill(quantity);
	await page.locator('input[name="capture-quantity"]').press("Enter");
}

export async function countAndPlace(operator: TestUser, assignmentId: string, locationId: string, quantity: number) {
	const login = await call("POST", "/auth/login", { body: { username: operator.username, password: operator.password }, channel: "app" });
	const counted = await call("POST", `/receipts/assignments/${assignmentId}/count`, {
		token: login.json.token,
		body: { countedQuantity: quantity },
		channel: "app",
		headers: { "Idempotency-Key": uniqueName("count-") },
	});
	expect(counted.status).toBe(200);
	const placed = await call("POST", `/receipts/assignments/${assignmentId}/placements`, {
		token: login.json.token,
		body: { placements: [{ locationId, quantity }] },
		channel: "app",
		headers: { "Idempotency-Key": uniqueName("place-") },
	});
	expect(placed.status).toBe(200);
}
