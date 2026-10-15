// F0-11 · e2e: Spanish by default, English on request, and the choice stays after a reload.
import { test, expect } from "../support/test";

test("the page is in Spanish by default and switches to English", async ({ page }) => {
  await page.goto("/#/login");
  await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();

  await page.getByRole("combobox", { name: "Idioma" }).selectOption("en");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});
