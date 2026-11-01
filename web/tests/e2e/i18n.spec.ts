// F0-11 · e2e: Spanish by default, English on request in the system's top bar, and the choice stays after a reload.
import { test, expect } from "../support/test";
import { newUser, signInAs } from "../support/api";

test("the entry screen is in Spanish, and the language of the top bar switches the system and stays", async ({ page }) => {
  await page.goto("/#/login");
  await expect(page.getByRole("heading", { name: "StockFlow" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();

  // The language lives in the system, not on the entry screen.
  const office = await newUser(["OFFICE"]);
  await signInAs(page, office);
  // The session is read on a fresh load of the document.
  await page.reload();
  await expect(page.getByRole("banner")).toBeVisible();
  // The profile is saved in the background; the reload has to wait for that answer.
  const saved = page.waitForResponse((response) => response.url().endsWith("/auth/me") && response.request().method() === "PATCH");
  await page.getByRole("banner").getByRole("combobox", { name: "Idioma" }).selectOption("en");
  await expect(page.getByText(`Hello, ${office.name.split(" ")[0]}`)).toBeVisible();
  expect((await saved).status()).toBe(200);

  await page.reload();
  await expect(page.getByText(`Hello, ${office.name.split(" ")[0]}`)).toBeVisible();
});
