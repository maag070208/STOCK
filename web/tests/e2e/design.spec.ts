// F0-18 · e2e: the web follows design/command-center-DESIGN.md. Expected values are read from the tokens, not typed here.
import { test, expect, type Page } from "@playwright/test";
import { designTokens } from "../../src/shared/ui/design-tokens";
import { newUser, signInAs, type TestUser } from "../support/api";

const colors = designTokens.colors;

// The workspace and the shell are private: those tests sign in as an office user.
let office: TestUser;
test.beforeAll(async () => {
  office = await newUser(["OFFICE"]);
});
const components = designTokens.components;

function rgb(hex: string): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
}
// "rgba(0, 0, 0, 0.70)" in the document is "rgba(0, 0, 0, 0.7)" in the browser.
const browserColor = (value: string) => value.replace(/0\.(\d)0\)/, "0.$1)");
const px = (value: string) => Number(value.replace("px", ""));

async function style(page: Page, selector: string, property: string): Promise<string> {
  return page.locator(selector).first().evaluate((el, prop) => getComputedStyle(el).getPropertyValue(prop), property);
}

test.describe("workspace", () => {
  test("the canvas is warm off-white with brown-gray text in the system sans font", async ({ page }) => {
    await signInAs(page, office);
    await page.goto("/#/");
    expect(await style(page, "body", "background-color")).toBe(rgb(colors.background));
    expect(await style(page, "body", "color")).toBe(rgb(colors["on-background"]));
    expect(await style(page, "body", "font-family")).toMatch(/^ui-sans-serif/);
  });

  test("dark mode uses the dark workspace colors", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "dark" });
    await signInAs(page, office);
    await page.goto("/#/");
    await expect(page.locator("html")).toHaveClass(/dark/);
    expect(await style(page, "body", "background-color")).toBe(rgb(colors["dark-background"]));
    expect(await style(page, "body", "color")).toBe(rgb(colors["dark-on-surface"]));
  });

  test("the content is capped at the container width and its title is the module headline", async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 900 });
    await signInAs(page, office);
    await page.goto("/#/");
    const heading = page.getByRole("heading", { name: "Inicio" });
    const container = page.locator('main [class*="sf:max-w-"]').first();
    expect((await container.boundingBox())!.width).toBe(px(designTokens.layout["container-max"]));
    expect(await heading.evaluate((el) => getComputedStyle(el).fontSize)).toBe("24px");
    expect(await heading.evaluate((el) => getComputedStyle(el).fontWeight)).toBe(String(designTokens.typography["headline-lg"].fontWeight));
  });
});

test.describe("shell", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await signInAs(page, office);
    await page.goto("/#/");
    await expect(page.getByRole("navigation").first()).toBeVisible();
  });

  test("the sidebar is black glass: 70% black, 12px blur, faint white border, 256px wide", async ({ page }) => {
    expect(await style(page, "aside", "background-color")).toBe(browserColor(components["shell-sidebar"].backgroundColor));
    expect(await style(page, "aside", "backdrop-filter")).toBe(`blur(${designTokens.elevation["shell-blur"]})`);
    expect(await style(page, "aside", "border-right-color")).toBe("rgba(255, 255, 255, 0.1)");
    const box = await page.locator("aside").first().boundingBox();
    expect(box!.width).toBe(px(designTokens.layout["shell-sidebar-expanded"]));
  });

  test("the top bar is black glass and 64px tall", async ({ page }) => {
    expect(await style(page, "header", "background-color")).toBe(browserColor(components["shell-header"].backgroundColor));
    expect(await style(page, "header", "backdrop-filter")).toBe(`blur(${designTokens.elevation["shell-blur"]})`);
    const box = await page.locator("header > div").first().boundingBox();
    expect(box!.height).toBe(px(designTokens.layout["shell-header-height"]));
  });

  test("the active navigation item is solid orange with white text", async ({ page }) => {
    const item = page.getByRole("navigation").first().getByRole("button", { name: "Inicio" });
    expect(await item.evaluate((el) => getComputedStyle(el).backgroundColor)).toBe(rgb(colors.primary));
    const label = item.getByText("Inicio", { exact: true });
    expect(await label.evaluate((el) => getComputedStyle(el).color)).toBe(rgb(colors["on-primary"]));
  });

  test("navigation items have the design's corner and padding, not the kit's pill", async ({ page }) => {
    const item = page.getByRole("navigation").first().getByRole("button", { name: "Inicio" });
    expect(await item.evaluate((el) => getComputedStyle(el).borderTopLeftRadius)).toBe(components["nav-item"].rounded);
    expect(await item.evaluate((el) => getComputedStyle(el).paddingBlock)).toBe("6px");
    expect(await item.evaluate((el) => getComputedStyle(el).paddingInline)).toBe("12px");
  });

  test("collapsing the sidebar leaves it 64px wide, with the brand mark and the item's icon", async ({ page }) => {
    await page.getByRole("banner").getByRole("button").first().click();
    await expect.poll(async () => (await page.locator("aside").first().boundingBox())!.width).toBe(
      px(designTokens.layout["shell-sidebar-collapsed"]),
    );
    const rail = page.locator("aside").first();
    await expect(rail.locator('img[src="/brand/stockflow-glyph-dark.svg"]')).toBeVisible();
    await expect(rail.getByRole("button", { name: "Inicio" }).locator("svg")).toBeVisible();
  });
});

test.describe("login", () => {
  // The entry screen is built only with the Axzy UI System (see DOCUMENTACION_DETALLADA_COMPONENTES.txt).
  test("the entry screen shows the brand, the sign-in form and the language field", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto("/#/login");
    await expect(page.getByTestId("brand-intro")).toBeHidden({ timeout: 8000 });

    await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
    await expect(page.getByLabel("Usuario", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Contraseña", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();
    await expect(page.getByRole("combobox", { name: "Idioma" })).toBeVisible();
  });

  test("the language field switches the screen without leaving it", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto("/#/login");
    await page.getByRole("combobox", { name: "Idioma" }).selectOption("en");
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });

  test("on a phone the entry screen stacks and nothing scrolls sideways", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto("/#/login");
    await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
