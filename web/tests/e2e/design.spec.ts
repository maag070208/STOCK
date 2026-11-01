// F0-18 · e2e: the web follows design/stockflow-DESIGN.md and the kit's bento appearance. Expected values are read from
// the tokens, not typed here.
import { test, expect, type Page } from "../support/test";
import { designTokens } from "../../src/shared/ui/design-tokens";
import { newUser, signInAs, type TestUser } from "../support/api";

const colors = designTokens.colors;

// The workspace and the shell are private: those tests sign in as an office user.
let office: TestUser;
test.beforeAll(async () => {
  office = await newUser(["OFFICE"]);
});

function rgb(hex: string): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
}
const px = (value: string) => Number(value.replace("px", ""));

async function style(page: Page, selector: string, property: string): Promise<string> {
  return page.locator(selector).first().evaluate((el, prop) => getComputedStyle(el).getPropertyValue(prop), property);
}

test.describe("workspace", () => {
  test("the canvas is the bento gray with ink text in the system sans font", async ({ page }) => {
    await signInAs(page, office);
    await page.goto("/#/");
    expect(await style(page, "body", "background-color")).toBe(rgb(colors.background));
    expect(await style(page, "body", "color")).toBe(rgb(colors["on-background"]));
    expect(await style(page, "body", "font-family")).toMatch(/^ui-sans-serif/);
  });

  test("dark mode uses the dark workspace colors", async ({ page }) => {
    // The design is light-first; the dark mode is chosen by the kit's own control, which keeps it in localStorage.
    await page.addInitScript(() => window.localStorage.setItem("it-theme-dark-mode", "dark"));
    await signInAs(page, office);
    await page.goto("/#/");
    await expect(page.locator("html")).toHaveClass(/dark/);
    expect(await style(page, "body", "background-color")).toBe(rgb(colors["dark-background"]));
    expect(await style(page, "body", "color")).toBe(rgb(colors["dark-on-surface"]));
  });

  test("a screen taller than the window ends with the layout's room", async ({ page }) => {
    // The bottom margin belongs to the layout (web.md section 8.6 rule 4): a long screen used to overflow the
    // container that carried the padding, so its last card ended against the edge of the window.
    await page.setViewportSize({ width: 1024, height: 420 });
    await signInAs(page, office);
    await page.goto("/#/products");
    await expect(page.getByRole("heading", { name: "Materiales" })).toBeVisible();
    // The rows arrive after the title, and they are what makes the screen long.
    await page.waitForLoadState("networkidle");
    await page.mouse.move(500, 200);
    for (let wheel = 0; wheel < 12; wheel++) await page.mouse.wheel(0, 400);
    const room = await page.evaluate(() => {
      const main = document.querySelector("main")!;
      const padded = document.querySelector('[class*="pb-12"]')!;
      const last = padded.lastElementChild!;
      return {
        bottom: Math.round(main.getBoundingClientRect().bottom - last.getBoundingClientRect().bottom),
        padding: Math.round(Number.parseFloat(getComputedStyle(padded).paddingBottom)),
        taller: main.scrollHeight > main.clientHeight,
      };
    });
    // The screen really is taller than the window, and what is left below the content is the layout's padding.
    expect(room.taller).toBe(true);
    expect(room.bottom).toBe(room.padding);
  });

  test("the content is capped at the container width and the home opens with its greeting", async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 900 });
    await signInAs(page, office);
    await page.goto("/#/");
    const container = page.locator('main [class*="sf:max-w-"]').first();
    expect((await container.boundingBox())!.width).toBe(px(designTokens.layout["container-max"]));
    // The home is a bento dashboard: it greets the person instead of repeating the module title.
    const heading = page.getByRole("heading", { level: 1 });
    await expect(heading).toContainText("Hola,");
    expect(await heading.evaluate((el) => getComputedStyle(el).fontWeight)).toBe(String(designTokens.typography["headline-xl"].fontWeight));
  });
});

test.describe("shell", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await signInAs(page, office);
    await page.goto("/#/");
    await expect(page.getByRole("navigation").first()).toBeVisible();
  });

  test("the sidebar is the kit's bento card: white, rounded and floating over the canvas", async ({ page }) => {
    const card = page.locator("aside div").first();
    expect(await card.evaluate((el) => getComputedStyle(el).backgroundColor)).toBe(rgb(colors.surface));
    expect(px(await card.evaluate((el) => getComputedStyle(el).borderTopLeftRadius))).toBeGreaterThan(16);
  });

  test("the menu stays open with its names, and the top bar folds it into a rail that is remembered", async ({ page }) => {
    const menu = page.locator("aside").first();
    await expect.poll(async () => (await menu.boundingBox())!.width).toBe(px(designTokens.layout["shell-sidebar-expanded"]));
    await page.locator("header").getByRole("button").first().click();
    await expect.poll(async () => (await menu.boundingBox())!.width).toBe(px(designTokens.layout["shell-sidebar-collapsed"]));
    await page.reload();
    await expect.poll(async () => (await menu.boundingBox())!.width).toBe(px(designTokens.layout["shell-sidebar-collapsed"]));
  });

  test("Cmd+K goes anywhere: a few letters and Enter open the screen", async ({ page }) => {
    await page.keyboard.press("ControlOrMeta+k");
    await page.getByLabel("Busca una pantalla, factura, orden o material").fill("factur");
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/#\/invoices$/);
  });

  test("the top bar is a white bento card of the design's height", async ({ page }) => {
    expect(await style(page, "header", "background-color")).toBe(rgb(colors.surface));
    const box = await page.locator("header > div").first().boundingBox();
    expect(box!.height).toBe(px(designTokens.layout["shell-header-height"]));
  });

  test("the current navigation item carries a surface and its label is the ink text", async ({ page }) => {
    const current = page.locator('aside [aria-label="Inicio"]').first();
    const bg = await current.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(bg).not.toBe("rgba(0, 0, 0, 0)");
    expect(await current.evaluate((el) => getComputedStyle(el).color)).toBe(rgb(colors["on-background"]));
    // Every other item is transparent: only the current one carries a surface.
    const other = page.locator('aside [role="button"][aria-label="Reportes"]').first();
    expect(await other.evaluate((el) => getComputedStyle(el).backgroundColor)).toBe("rgba(0, 0, 0, 0)");
  });

  test("the menu is grouped in sections, and the section of the current screen stays open", async ({ page }) => {
    const rail = page.locator("aside").first();
    const operation = rail.getByRole("button", { name: "Operación" });
    await expect(operation).toHaveAttribute("aria-expanded", "false");
    await operation.click();
    await rail.getByRole("button", { name: "Facturas" }).click();
    await expect(page).toHaveURL(/#\/invoices$/);
    await expect(operation).toHaveAttribute("aria-expanded", "true");
  });

  test("the top bar switches the system between light and dark", async ({ page }) => {
    await page.getByRole("button", { name: "Modo oscuro" }).click();
    await expect(page.locator("html")).toHaveClass(/dark/);
    await page.getByRole("button", { name: "Modo claro" }).click();
    await expect(page.locator("html")).not.toHaveClass(/dark/);
  });

  test("the rail shows the brand mark and the icon of each item", async ({ page }) => {
    const rail = page.locator("aside").first();
    await expect(rail.locator('img[src="/brand/stockflow-glyph-light.svg"]').first()).toBeVisible();
    await expect(rail.getByRole("button", { name: "Inicio" })).toBeVisible();
  });
});

test.describe("login", () => {
  // The entry screen is built only with the Axzy UI System (see DOCUMENTACION_DETALLADA_COMPONENTES.txt).
  test("the entry screen splits into the brand, the form and the illustration, with no language control", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto("/#/login");
    await expect(page.getByTestId("brand-intro")).toBeHidden({ timeout: 8000 });

    await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
    await expect(page.getByLabel("Usuario", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Contraseña", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();
    // The language is chosen inside the system (the top bar), not on the entry screen.
    await expect(page.getByRole("combobox", { name: "Idioma" })).toHaveCount(0);
    // The right panel is the flat illustration of the product.
    await expect(page.locator("main svg").first()).toBeVisible();
  });

  test("on a phone the entry screen stacks and nothing scrolls sideways", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto("/#/login");
    await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
