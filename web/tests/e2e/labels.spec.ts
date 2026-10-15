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

	const editor = page.getByLabel("ZPL");
	await editor.fill(zpl.replace("^PQ", "^HH\n^PQ"));
	await expect(report.getByText("La plantilla usa un comando ZPL que no está permitido.")).toBeVisible();
	await expect(report.getByText("^HH · Línea 3")).toBeVisible();
	// Saving it anyway: the API refuses it, with the same message on the field, and no version is created.
	await page.getByRole("button", { name: "Guardar" }).click();
	await expect(page.getByText("La plantilla usa un comando ZPL que no está permitido.").nth(1)).toBeVisible();

	await editor.fill(zpl.replace("30,30", "40,40"));
	await expect(report.getByText("La plantilla cumple las reglas.")).toBeVisible();
	await page.getByRole("button", { name: "Guardar" }).click();
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
	await page.getByLabel("ZPL").fill(zpl.replace("{{copies}}", "{{rack}}"));
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
