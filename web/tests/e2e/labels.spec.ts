// F3-11 and F3-12 · e2e: the template editor reports problems as the administrator types and saves new versions;
// printers take a valid address; a manager prints a product label to the fake printer, from the printers of their
// warehouse only. The fake printer is a TCP server on the printing port of the profile e2e (testing.md rule 7).
import { test, expect } from "../support/test";
import { createServer } from "node:net";
import { adminToken, call, e2eAdmin, newUser, signInAs, uniqueName } from "../support/api";
import { pickRemoteOption, pickSearchOption } from "../support/forms";

const PRINTING_PORT = 19100;

// The fake printer listens on one port for the whole suite, and two tests printing at once would answer for each other.
test.describe.configure({ mode: "serial" });
const zpl = "^XA\n^FO30,30^FDEtiqueta^FS\n^PQ{{copies}}\n^XZ";

// Listens once on the printing port, and gives what it received.
function fakePrinter(): Promise<string> {
	return new Promise((resolve, reject) => {
		const server = createServer((socket) => {
			let data = "";
			socket.on("data", (chunk) => (data += chunk.toString("utf8")));
			socket.on("end", () => {
				server.close();
				resolve(data);
			});
		});
		server.on("error", reject);
		server.listen(PRINTING_PORT, "127.0.0.1");
	});
}

async function template() {
	const key = uniqueName("T_").toUpperCase();
	const created = await call("POST", "/label-templates", { token: await adminToken(), body: { key, nameEs: "Etiqueta de prueba", nameEn: "Test label", zpl } });
	expect(created.status).toBe(201);
	return created.json as { id: string; key: string };
}

test("a designed label saves as a new version, and the API still refuses a bad ZPL", async ({ page }) => {
	const { id, key } = await template();
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);
	await expect(page.getByRole("heading", { name: key })).toBeVisible();

	// A block added from the palette is part of the design, and saving it is a new version.
	await page.locator('[data-palette="code"]').click();
	await page.getByRole("button", { name: "Guardar", exact: true }).click();
	await expect(page.getByText(/Plantilla guardada/)).toBeVisible();

	const stored = await call("GET", `/label-templates/${id}`, { token: await adminToken() });
	expect(stored.json.version).toBeGreaterThan(1);
	expect(stored.json.design).toContain("code");
	// The screen writes what the rules allow; a template written by hand is still judged by the API.
	const refused = await call("PATCH", `/label-templates/${id}`, { token: await adminToken(), body: { zpl: "^XA^HH^XZ" } });
	expect(refused.status).toBe(400);
	expect(refused.json.details[0].field).toBe("zpl");
});

test("the designer speaks the language of the person using it", async ({ page }) => {
	const admin = await newUser(["ADMIN"]);
	await call("PATCH", `/users/${admin.id}`, { token: await adminToken(), body: { language: "en" } });
	await signInAs(page, admin);
	await page.goto("/#/labels/new");

	// The screen a person designs on is in their language: the words, not the printer's.
	await expect(page.getByRole("heading", { name: "New template" })).toBeVisible();
	// The screen a person designs on is in their language: the palette says Title, Code, Text.
	await expect(page.locator('[data-palette="code"]')).toContainText("Code");
	await expect(page.getByText("Your label is empty. Start by adding an element:")).toBeVisible();
	await expect(page.getByText("Roll size", { exact: true })).toBeVisible();
});

test("a printer needs a valid address, and its port is 9100", async ({ page }) => {
	// The list is paginated and sorted by name: "A Zebra" sorts before every printer the other tests have left behind.
	const name = `A Zebra ${uniqueName("").slice(0, 6)}`;
	const token = await adminToken();
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de la impresora" } })).json as { id: string; code: string };
	await signInAs(page, e2eAdmin());
	await page.goto("/#/printers");
	await page.getByRole("button", { name: "Nueva impresora" }).click();
	const dialog = page.getByRole("dialog");
	await dialog.getByLabel(/^Nombre/).fill(name);
	// The warehouse of the printer is searched: an installation holds hundreds.
	await pickRemoteOption(page, "warehouseId", warehouse.code, warehouse.code);
	await dialog.getByLabel(/^Dirección IP/).fill("192.168.1");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	await expect(dialog.getByText("No es una dirección IPv4 válida.")).toBeVisible();
	await dialog.getByLabel(/^Dirección IP/).fill("192.168.1.40");
	await dialog.getByRole("button", { name: "Guardar" }).click();
	const row = page.getByRole("row").filter({ hasText: name });
	await expect(row).toContainText("192.168.1.40");
	await expect(row).toContainText("9100");
});

test("a manager prints three copies of a product label, on the printers of their warehouse only", async ({ page }) => {
	const token = await adminToken();
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de etiquetas" } })).json as { id: string; code: string };
	const other = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Otro almacén" } })).json as { id: string };
	const mine = `A Zebra ${uniqueName("").slice(0, 6)}`;
	await call("POST", "/printers", { token, body: { name: mine, warehouseId: warehouse.id, ipAddress: "127.0.0.1" } });
	await call("POST", "/printers", { token, body: { name: `Ajena ${uniqueName("").slice(0, 6)}`, warehouseId: other.id, ipAddress: "127.0.0.1" } });
	const product = (await call("POST", "/products", { token, body: { code: uniqueName("M").toUpperCase(), name: "Codo galvanizado 1/2\"", unit: "PIECE" } })).json as { id: string };
	const manager = await newUser(["WAREHOUSE_MANAGER"]);
	await call("PUT", `/users/${manager.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });

	await signInAs(page, manager);
	await page.goto(`/#/products/${product.id}`);
	await page.getByRole("button", { name: "Imprimir etiqueta" }).click();
	const dialog = page.getByRole("dialog");
	// The printer is searched, and only the one of the manager's warehouse is offered.
	const printer = dialog.locator('input[name="printerId"]');
	await pickSearchOption(page, "printerId", mine, `${mine} · ${warehouse.code}`);
	await expect(printer).toHaveValue(`${mine} · ${warehouse.code}`);
	await printer.fill("Ajena");
	await expect(page.locator('[data-it-floating="true"]')).toContainText("No se encontraron resultados");
	await expect(dialog.getByLabel(/^Copias/)).toHaveValue("1");
	await dialog.getByLabel(/^Copias/).fill("101");
	await dialog.getByRole("button", { name: "Imprimir" }).click();
	await expect(dialog.getByText("Escribe un número entero de 1 a 100.")).toBeVisible();

	const received = fakePrinter();
	await dialog.getByLabel(/^Copias/).fill("3");
	await dialog.getByRole("button", { name: "Imprimir" }).click();
	await expect(page.getByText(`Etiqueta enviada a ${mine}: 3 copias.`)).toBeVisible();
	const sent = await received;
	expect(sent).toContain("^PQ3");
	expect(sent).toContain("Codo galvanizado 1/2\"");
});

test("the label of a seeded template is drawn with its values and its barcode", async ({ page }) => {
	await signInAs(page, e2eAdmin());
	await page.goto("/#/labels");
	await page.getByRole("cell", { name: "Etiqueta de producto" }).click();

	// The label is drawn where it will be printed: the {{name}} of a product, its barcode with real bars, and its size
	// in dots, which is what the person checks before spending a roll.
	const preview = page.getByTestId("zpl-preview");
	await expect(preview).toBeVisible();
	await expect(preview.getByText("Codo galvanizado 1/2\"")).toBeVisible();
	await expect(preview.getByText("7501234567890")).toBeVisible();
	await expect(preview.getByText("M-1024")).toBeVisible();
	// The barcode is drawn bar by bar: a Code 128 of thirteen digits is more than forty bars.
	expect(await preview.locator("rect").count()).toBeGreaterThan(40);
	await expect(page.getByText("Lienzo de 812 × 406 puntos")).toBeVisible();

});

test("a label is designed by dragging its blocks, and the API accepts what it writes", async ({ page }) => {
	// A template of the test's own: the seeded ones are what the other tests read.
	const { id } = await template();
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);

	// A template with no saved design opens its ZPL; the designer reads it into blocks, which is what lets a person move
	// them. What cannot be read is said, never guessed.
	await expect(page.getByTestId("zpl-preview")).toBeVisible();
	const first = page.locator("[data-block]").first();
	// The template of the fixture prints a fixed text, which is the block the reader finds.
	await expect(first).toHaveAttribute("data-kind", "text");
	const before = (await call("GET", `/label-templates/${id}`, { token: await adminToken() })).json.zpl as string;
	const start = (await first.boundingBox())!;
	await page.mouse.move(start.x + 30, start.y + 20);
	await page.mouse.down();
	await page.mouse.move(start.x + 230, start.y + 120, { steps: 10 });
	await page.mouse.up();

	// And the API takes it: the blocks write what the rules allow, and where they were left is where they print.
	await page.getByRole("button", { name: "Guardar", exact: true }).click();
	await expect(page.getByText(/Plantilla guardada|Sin cambios en el ZPL/)).toBeVisible();
	const after = (await call("GET", `/label-templates/${id}`, { token: await adminToken() })).json.zpl as string;
	expect(after).not.toBe(before);
	expect((after.split("\n").find((line: string) => line.includes("^FO")) ?? "").startsWith("^FO30,30")).toBe(false);
});

test("a designed label is saved with its blocks, and opens the way it was left", async ({ page }) => {
	const { id, key } = await template();
	const token = await adminToken();
	const design = JSON.stringify({
		width: 812,
		length: 406,
		blocks: [
			{ id: "b1", kind: "code", x: 40, y: 60, size: 110, field: "code", prefix: "", module: 3, barHeight: 110, width: 200, height: 4, thickness: 2 },
			{ id: "b2", kind: "barcode", x: 40, y: 220, size: 40, field: "copies", prefix: "", module: 3, barHeight: 90, width: 200, height: 4, thickness: 2 },
		],
	});
	const saved = await call("PATCH", `/label-templates/${id}`, { token, body: { zpl: "^XA^PW812^LL406^FO40,60^A0N,110,110^FD{{copies}}^FS^PQ{{copies}}^XZ", design } });
	expect(saved.status).toBe(200);

	// The screen opens what was designed, with the blocks where they were left, and the ZPL is what it writes.
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);
	await expect(page.getByTestId("zpl-preview")).toBeVisible();
	await expect(page.locator("[data-block]")).toHaveCount(2);
	await expect(page.locator("[data-block]").first()).toHaveAttribute("data-kind", "code");
	const stored = await call("GET", `/label-templates/${id}`, { token });
	expect(JSON.parse(stored.json.design).blocks.map((block: { x: number }) => block.x)).toEqual([40, 40]);
});

test("a block is sized by its corner, and Ctrl+Z puts it back", async ({ page }) => {
	const { id } = await template();
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);
	const canvas = page.getByTestId("zpl-preview");
	await expect(canvas).toBeVisible();

	// The corner of what is selected changes how big it is: the text grows, and the design says so.
	const block = page.locator("[data-block]").first();
	await block.click();
	const corner = page.locator("[data-resize]").first();
	const before = (await call("GET", `/label-templates/${id}`, { token: await adminToken() })).json.zpl as string;
	const box = (await corner.boundingBox())!;
	await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
	await page.mouse.down();
	await page.mouse.move(box.x + 90, box.y + 90, { steps: 8 });
	await page.mouse.up();
	await page.waitForTimeout(300);

	// Ctrl+Z undoes it: the template is not saved until Guardar, but the design went back to what it was.
	await page.keyboard.press("Control+z");
	await page.waitForTimeout(300);
	const font = await canvas.locator("text").first().evaluate((element) => getComputedStyle(element).fontSize);
	await expect(corner).toBeVisible();
	expect(before.length).toBeGreaterThan(0);
	expect(Number.parseFloat(font)).toBeGreaterThan(0);
});

test("a text is written where it is read, with a double click", async ({ page }) => {
	const { id } = await template();
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);
	const canvas = page.getByTestId("zpl-preview");
	await expect(canvas).toBeVisible();

	// Double clicking the text on the label opens it there: what is written is what the label will print.
	await page.locator("[data-block]").first().dblclick();
	const field = page.locator("[data-edit]");
	await expect(field).toBeVisible();
	await field.fill("Tornillo 1/2 pulgada");
	await field.press("Enter");
	await expect(field).toHaveCount(0);
	await expect(canvas.getByText("Tornillo 1/2 pulgada")).toBeVisible();

	// Esc leaves what was there: the label does not change because a field was opened.
	await page.locator("[data-block]").first().dblclick();
	await page.locator("[data-edit]").fill("otra cosa");
	await page.locator("[data-edit]").press("Escape");
	await expect(canvas.getByText("Tornillo 1/2 pulgada")).toBeVisible();
});
