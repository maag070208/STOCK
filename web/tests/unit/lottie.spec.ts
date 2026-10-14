// F0-16 · unit: each Lottie file parses and has the frame count of brand.md section 4; the player picks the variant by theme.
import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { animationPath } from "../../src/shared/brand/animations";

const here = dirname(fileURLToPath(import.meta.url));
const animations = join(here, "..", "..", "public", "animations");

const expected = { intro: 130, loader: 120 } as const;

for (const kind of ["intro", "loader"] as const) {
  for (const theme of ["light", "dark"] as const) {
    test(`stockflow-${kind}-${theme}.json parses and has ${expected[kind]} frames at 30 fps`, () => {
      const json = JSON.parse(readFileSync(join(animations, `stockflow-${kind}-${theme}.json`), "utf8"));
      expect(json.op).toBe(expected[kind]);
      expect(json.fr).toBe(30);
      expect(json.w).toBe(512);
      expect(json.h).toBe(512);
    });
  }
}

test("the player picks the light or dark variant from the theme", () => {
  expect(animationPath("intro", "light")).toBe("/animations/stockflow-intro-light.json");
  expect(animationPath("intro", "dark")).toBe("/animations/stockflow-intro-dark.json");
  expect(animationPath("loader", "dark")).toBe("/animations/stockflow-loader-dark.json");
});
