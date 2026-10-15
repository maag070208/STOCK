// F0-16 · e2e: the intro plays once before sign-in, the loader shows while the animation loads, and reduced motion stops the movement.
import { test, expect } from "../support/test";

test("the intro plays once, then sign-in is shown", async ({ page }) => {
  await page.goto("/#/login");
  await expect(page.getByTestId("brand-intro")).toBeVisible();
  await expect(page.getByTestId("brand-intro")).toBeHidden({ timeout: 8000 });
  await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
});

test("the loader shows while the intro animation is still loading", async ({ page }) => {
  await page.route("**/animations/stockflow-intro-light.json", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    await route.continue();
  });
  await page.goto("/#/login");
  await expect(page.getByRole("status", { name: "Cargando StockFlow" })).toBeVisible();
});

test("with reduced motion the intro stays still and does not block sign-in", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/#/login");
  await expect(page.getByTestId("brand-intro")).toHaveAttribute("data-motion", "reduced");
  await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible({ timeout: 3000 });
});
