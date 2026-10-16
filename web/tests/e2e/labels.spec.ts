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

test("the editor reports a forbidden command as it is typed, and each saved change is a new version", async ({ page }) => {
	const { id, key } = await template();
	await signInAs(page, e2eAdmin());
	await page.goto(`/#/labels/${id}`);
	await expect(page.getByRole("heading", { name: key })).toBeVisible();
	await expect(page.getByText("Etiqueta de prueba").first()).toBeVisible();
	const report = page.getByTestId("zpl-report");
	await expect(report.getByText("La plantilla cumple las reglas.")).toBeVisible();

	// The label of the test's own template is drawn too, with the one placeholder its key allows.
	await expect(page.getByTestId("zpl-preview").getByText("Etiqueta")).toBeVisible();

	const editor = page.getByRole("textbox", { name: "ZPL" });
	await editor.fill(zpl.replace("^PQ", "^HH\n^PQ"));
	await expect(report.getByText("La plantilla usa un comando ZPL que no está permitido.")).toBeVisible();
	await expect(report.getByText("^HH · Línea 3")).toBeVisible();
	// Saving it anyway: the API refuses it, with the same message on the field, and no version is created.
	await page.getByRole("button", { name: "Guardar", exact: true }).click();
	await expect(page.getByText("La plantilla usa un comando ZPL que no está permitido.").nth(1)).toBeVisible();

	await editor.fill(zpl.replace("30,30", "40,40"));
	await expect(report.getByText("La plantilla cumple las reglas.")).toBeVisible();
	await page.getByRole("button", { name: "Guardar", exact: true }).click();
	await expect(page.getByText("Plantilla guardada. Versión 2.")).toBeVisible();
	const versions = page.getByTestId("template-versions");
	await expect(versions.locator("[data-version]")).toHaveCount(2);
	await expect(versions.locator('[data-version="2"]')).toContainText("Activa");
});

test("for a person who reads English, the report is in English", async ({ page }) => {
	const { id } = await template();
	// The interface follows the language of the person's profile.
	const admin = await newUser(["ADMIN"]);
	await call("PATCH", `/users/${admin.id}`, { token: await adminToken(), body: { language: "en" } });
	await signInAs(page, admin);
	await page.goto(`/#/labels/${id}`);
	await page.getByRole("textbox", { name: "ZPL" }).fill(zpl.replace("{{copies}}", "{{rack}}"));
	await expect(page.getByTestId("zpl-report").getByText("The template uses a field this label type does not have.")).toBeVisible();
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

test("the editor draws the label of a product template, with its values and its barcode", async ({ page }) => {
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

	// What is typed is what is drawn: a shorter label moves the text and shrinks the canvas.
	const editor = page.getByRole("textbox", { name: "ZPL" });
	await editor.fill(`^XA
^PW600
^LL300
^FO20,20^A0N,50,50^FD{{code}}^FS
^PQ1
^XZ`);
	await expect(page.getByText("Lienzo de 600 × 300 puntos")).toBeVisible();
	await expect(preview.getByText("M-1024")).toBeVisible();
	await expect(preview.getByText("7501234567890")).toHaveCount(0);
});

test("a label is designed by dragging its blocks, and the API accepts what it writes", async ({ page }) => {
	await signInAs(page, e2eAdmin());
	await page.goto("/#/labels");
	await page.getByRole("cell", { name: "Etiqueta de producto" }).click();

	// A template with no saved design opens its ZPL; the designer reads it into blocks, which is what lets a person move
	// them. What cannot be read is said, never guessed.
	await page.getByRole("button", { name: "Diseño" }).click();
	await expect(page.getByTestId("zpl-preview")).toBeVisible();
	const first = page.locator("[data-block]").first();
	await expect(first).toHaveAttribute("data-kind", "title");
	await page.getByRole("button", { name: "Ver el ZPL que se guardará" }).click();
	const before = await page.getByTestId("designed-zpl").innerText();
	const start = (await first.boundingBox())!;
	await page.mouse.move(start.x + 30, start.y + 20);
	await page.mouse.down();
	await page.mouse.move(start.x + 230, start.y + 120, { steps: 10 });
	await page.mouse.up();

	// What was dragged is where the template now says: the ZPL is written from the blocks.
	const moved = await page.getByTestId("designed-zpl").innerText();
	expect(moved).not.toBe(before);
	const firstOrigin = moved.split("\n").find((line) => line.includes("^FO"))!;
	expect(firstOrigin.startsWith("^FO30,30")).toBe(false);

	// And the API takes it: the blocks write what the rules allow.
	await page.getByRole("button", { name: "Guardar", exact: true }).click();
	await expect(page.getByText(/Plantilla guardada|Sin cambios en el ZPL/)).toBeVisible();
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
