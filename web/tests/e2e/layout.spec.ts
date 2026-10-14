// F0-10 · e2e: the shell shows the navigation and the flat appearance on desktop, and a phone opens the menu from the top bar.
import { test, expect } from "@playwright/test";
import { newUser, signInAs, type TestUser } from "../support/api";

// The shell is private: the tests sign in as an office user.
let office: TestUser;
test.beforeAll(async () => {
  office = await newUser(["OFFICE"]);
});

test("the shell shows the navigation and the flat appearance on desktop", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await signInAs(page, office);
  await page.goto("/#/");
  await expect(page.locator("html")).toHaveAttribute("data-it-appearance", "flat");
  await expect(page.getByRole("navigation").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
});

test("on a phone the top bar is shown and the menu opens the navigation", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await signInAs(page, office);
  await page.goto("/#/");
  await expect(page.getByRole("banner")).toBeVisible();
  await page.getByRole("button", { name: "Menú" }).click();
  await expect(page.getByRole("navigation").first()).toBeVisible();
});

for (const [name, width, height] of [["desktop", 1280, 800], ["phone", 375, 812]] as const) {
  test(`no horizontal scroll on ${name}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await signInAs(page, office);
  await page.goto("/#/");
    await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
    await expect(page.getByRole("banner")).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
}
