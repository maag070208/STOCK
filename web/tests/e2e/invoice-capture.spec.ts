// F8-05 to F8-08 · e2e: the fast invoice flow. One search box and three status chips on the list, a new invoice in a
// modal over it, the quick capture bar and the closing by hand on the detail, all with the keyboard.
import { expect, test, type Page } from "@playwright/test";
import { businessDate, call, newUser, signInAs, uniqueName } from "../support/api";
import { entriesFixture, type EntriesFixture } from "../support/entries";
import { captureMaterial, fillNewInvoice } from "../support/invoices";

// An invoice created through the API, as the office would have captured it before the screens: its lines, and nothing
// counted, so it is still open.
async function invoice(f: EntriesFixture, lines: { productId: string; quantity: number }[], number = uniqueName("FF-").toUpperCase()) {
	const created = await call("POST", "/invoices", {
		token: f.token,
		headers: { "Idempotency-Key": uniqueName("k") },
		body: { number, supplierId: f.supplier.id, invoiceDate: businessDate(), warehouseId: f.warehouse.id, lines },
	});
	expect(created.status, JSON.stringify(created.json)).toBe(201);
	return created.json as { id: string; number: string; lines: { id: string; product: { code: string } }[] };
}

// The number of an invoice is the title of its page.
const heading = (page: Page, number: string) => page.getByRole("heading", { name: new RegExp(number) });

// The chips of the list live in their own group: the empty state carries a button of its own.
const chip = (page: Page, name: string) => page.getByRole("button", { name, exact: true });

test.describe("the list", () => {
	test("one search box and the status chips filter together, and a row opens with the keyboard", async ({ page }) => {
		const f = await entriesFixture();
		const screws = await f.product("Tornillo de la lista");
		const open = await invoice(f, [{ productId: screws.id, quantity: 10 }]);
		const closed = await invoice(f, [{ productId: screws.id, quantity: 20 }]);
		expect((await call("POST", `/invoices/${closed.id}/closure`, { token: f.token })).status).toBe(200);
		const office = await newUser(["OFFICE"]);
		await signInAs(page, office);
		await page.goto("/#/invoices");

		// Both are there, and the supplier's name is what the box searches.
		await expect(page.getByRole("row").filter({ hasText: open.number })).toBeVisible();
		await page.getByLabel("Buscar por número o proveedor").fill(f.supplier.name);
		await expect(page.getByRole("row").filter({ hasText: open.number })).toBeVisible();

		// Cerradas leaves only the closed one.
		await chip(page, "Cerradas").click();
		await expect(page.getByRole("row").filter({ hasText: closed.number })).toBeVisible();
		await expect(page.getByRole("row").filter({ hasText: open.number })).toHaveCount(0);

		// The box and the chip are ANDed: half the closed number and the chip keep it, and nothing else.
		await page.getByLabel("Buscar por número o proveedor").fill(closed.number.slice(-6));
		await expect(page.getByRole("row").filter({ hasText: closed.number })).toBeVisible();
		await expect(page.getByRole("row").filter({ hasText: open.number })).toHaveCount(0);

		// A search with no match says so instead of showing an empty table.
		await page.getByLabel("Buscar por número o proveedor").fill("no-existe-esta-factura");
		await expect(page.getByText("No hay facturas con ese filtro.")).toBeVisible();

		// Todas brings both back, and the row opens the detail with the keyboard.
		await chip(page, "Todas").click();
		await page.getByLabel("Buscar por número o proveedor").fill("");
		const row = page.getByRole("row").filter({ hasText: open.number });
		await expect(row).toBeVisible();
		await row.focus();
		await row.press("Enter");
		await expect(heading(page, open.number)).toBeVisible();
	});
});

test.describe("the modal", () => {
	test("N opens it with the focus on the number, Esc closes it and returns the focus, and Enter saves and lands on the materials", async ({ page }) => {
		const f = await entriesFixture();
		const office = await newUser(["OFFICE"]);
		await signInAs(page, office);
		await page.goto("/#/invoices");

		// `N` opens the modal, with the focus on the number and the four fields, in the modal's own role.
		const opener = page.getByRole("button", { name: "Nueva factura" });
		await opener.click();
		const dialog = page.getByRole("dialog", { name: "Nueva factura" });
		await expect(dialog).toBeVisible();
		await expect(dialog).toHaveAttribute("aria-modal", "true");
		await expect(page.getByLabel("Número de factura")).toBeFocused();
		// Nothing else is captured here: the four fields, and no materials.
		await expect(dialog.getByLabel("Número de factura")).toBeVisible();
		await expect(dialog.locator('[name="supplierId"]')).toBeVisible();
		await expect(dialog.locator('input[name="invoiceDate"]')).toBeVisible();
		await expect(dialog.locator('select[name="warehouseId"]')).toBeVisible();
		await expect(dialog.getByRole("heading", { name: "Materiales", exact: true })).toHaveCount(0);
		await page.keyboard.press("Escape");
		await expect(dialog).toHaveCount(0);
		await expect(opener).toBeFocused();

		// With N, the keyboard opens it again.
		await page.keyboard.press("n");
		await expect(page.getByRole("dialog", { name: "Nueva factura" })).toBeVisible();
		// Submitting empty says what is missing and takes the focus there.
		await page.getByRole("button", { name: "Guardar y agregar materiales" }).click();
		await expect(page.getByText("Este campo es obligatorio.").first()).toBeVisible();
		await expect(page.getByLabel("Número de factura")).toBeFocused();

		// Enter is the primary action: it saves and goes to the detail, with the focus on the material.
		const number = uniqueName("FN-").toUpperCase();
		await fillNewInvoice(page, { number, supplier: f.supplier, warehouseId: f.warehouse.id });
		await page.getByLabel("Número de factura").press("Enter");
		await expect(heading(page, number)).toBeVisible();
		await expect(page.getByTestId("quick-capture").getByPlaceholder("Busca por código o nombre")).toBeFocused();
	});

	test("Guardar goes back to the list with its aviso, and the aviso carries the way to the materials", async ({ page }) => {
		const f = await entriesFixture();
		const office = await newUser(["OFFICE"]);
		await signInAs(page, office);
		await page.goto("/#/invoices");
		await page.getByRole("button", { name: "Nueva factura" }).click();
		const number = uniqueName("FG-").toUpperCase();
		await fillNewInvoice(page, { number, supplier: f.supplier, warehouseId: f.warehouse.id });
		await page.getByRole("button", { name: "Guardar", exact: true }).click();

		// Back on the list, with the aviso, and the invoice it names.
		await expect(page.getByText(`Factura ${number} creada`).first()).toBeVisible();
		await expect(page.getByRole("row").filter({ hasText: number })).toBeVisible();
		await page.getByRole("button", { name: "Agregar materiales" }).click();
		await expect(heading(page, number)).toBeVisible();
	});
});

// The row of one material in the lines table: the kit builds the table, so a line is a row of it.
function lineOf(page: Page, product: { code: string }) {
	return page.getByRole("row", { name: new RegExp(`^${product.code}`) });
}

test.describe("the detail", () => {
	test("five materials are captured with Enter, a repeated one adds to its line, and the invoice closes by hand", async ({ page }) => {
		const f = await entriesFixture();
		const office = await newUser(["OFFICE"]);
		const products = [await f.product("Codo"), await f.product("Tuerca"), await f.product("Clavo"), await f.product("Arandela"), await f.product("Cinta")];
		const target = await invoice(f, [], uniqueName("FC-").toUpperCase());
		await signInAs(page, office);
		await page.goto(`/#/invoices/${target.id}`);

		// Open, with nothing in it: the bar invites the first material, and there is nothing to close yet.
		await expect(page.getByTestId("quick-capture")).toBeVisible();
		await expect(page.getByText("Aún no has capturado materiales. Empieza con el material y la cantidad.")).toBeVisible();
		await expect(page.getByRole("button", { name: "Cerrar factura" })).toHaveCount(0);

		for (const [index, product] of products.entries()) {
			await captureMaterial(page, product, String((index + 1) * 10));
			await expect(lineOf(page, product)).toBeVisible();
		}
		await expect(page.getByText("5 líneas · 150 piezas")).toBeVisible();

		// The same material again adds to the line that exists: five lines, and the quantity grows.
		await captureMaterial(page, products[0], "5");
		await expect(page.getByText("5 líneas · 155 piezas")).toBeVisible();
		await expect(lineOf(page, products[0])).toContainText("15");

		// Quitar takes a line away.
		await lineOf(page, products[4]).getByRole("button", { name: "Quitar" }).click();
		await expect(page.getByText("4 líneas · 105 piezas")).toBeVisible();

		// Closing by hand leaves the invoice closed and read only.
		await page.getByRole("button", { name: "Cerrar factura" }).click();
		await expect(page.getByText("Factura cerrada").first()).toBeVisible();
		await expect(page.getByText("Cerrada").first()).toBeVisible();
		await expect(page.getByTestId("quick-capture")).toHaveCount(0);
		await expect(page.getByRole("button", { name: "Cerrar factura" })).toHaveCount(0);
	});

	test("a close the API refuses shows its reason", async ({ page }) => {
		const f = await entriesFixture();
		const [operator] = f.operators;
		const screws = await f.product("Tornillo del cierre");
		const office = await newUser(["OFFICE"]);
		const target = await invoice(f, [{ productId: screws.id, quantity: 10 }]);
		await signInAs(page, office);
		await page.goto(`/#/invoices/${target.id}`);
		await expect(page.getByRole("button", { name: "Cerrar factura" })).toBeVisible();

		// Somebody assigns and counts the line while this screen is open: the invoice closes itself, and by hand is refused.
		const assigned = await call("PUT", `/invoices/${target.id}/lines/${target.lines[0].id}/assignments`, { token: f.token, body: { assignments: [{ operatorId: operator.id, quantity: 10 }] } });
		expect(assigned.status).toBe(200);
		const assignment = assigned.json.assignments[0].id as string;
		const login = await call("POST", "/auth/login", { body: { username: operator.username, password: operator.password }, channel: "app" });
		await call("POST", `/receipts/assignments/${assignment}/count`, { token: login.json.token, body: { countedQuantity: 10 }, channel: "app" });

		await page.getByRole("button", { name: "Cerrar factura" }).click();
		await expect(page.getByText("La factura no está en el estado que permite esta acción.")).toBeVisible();
	});
});

test.describe("keyboard and accessibility", () => {
	test("the focus stays inside the modal, comes back to its opener, and the flow runs with reduced motion", async ({ page }) => {
		await page.emulateMedia({ reducedMotion: "reduce" });
		const f = await entriesFixture();
		const office = await newUser(["OFFICE"]);
		await signInAs(page, office);
		await page.goto("/#/invoices");

		const opener = page.getByRole("button", { name: "Nueva factura" });
		await opener.click();
		const dialog = page.getByRole("dialog", { name: "Nueva factura" });
		await expect(dialog).toBeVisible();

		// Tab walks the controls of the modal and never leaves it.
		for (let step = 0; step < 12; step++) {
			await page.keyboard.press("Tab");
			expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);
		}
		// Shift+Tab goes back, still inside.
		await page.keyboard.press("Shift+Tab");
		expect(await page.evaluate(() => Boolean(document.activeElement?.closest('[role="dialog"]')))).toBe(true);

		// The controls carry their own visible label.
		await expect(page.getByLabel("Número de factura")).toBeVisible();
		await expect(page.locator('[name="supplierId"]')).toBeVisible();
		await expect(page.locator('select[name="warehouseId"]')).toBeVisible();

		// Closing with Escape returns the focus to the button that opened it.
		await page.keyboard.press("Escape");
		await expect(dialog).toHaveCount(0);
		await expect(opener).toBeFocused();

		// And the whole flow still runs with the reduced motion the person asked for.
		await page.keyboard.press("n");
		await fillNewInvoice(page, { number: uniqueName("FR-").toUpperCase(), supplier: f.supplier, warehouseId: f.warehouse.id });
		await page.getByRole("button", { name: "Guardar y agregar materiales" }).click();
		await expect(page.getByTestId("quick-capture")).toBeVisible();
	});
});
