// F0-09 · e2e: the login page renders through the web shell.
import { test, expect } from "../support/test";

test("the login page renders", async ({ page }) => {
  await page.goto("/#/login");
  await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
});
