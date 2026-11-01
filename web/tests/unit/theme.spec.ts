// F0-18 · unit: the Axzy UI System theme comes from the design tokens, with nothing chosen by hand.
import { test, expect } from "@playwright/test";
import { designTokens } from "../../src/shared/ui/design-tokens";
import { themeSettings } from "../../src/shared/ui/theme";

const colors = designTokens.colors;

test("the accent and the semantic colors are the design's", () => {
  expect(themeSettings.palette.primary).toBe(colors.primary);
  expect(themeSettings.palette.danger).toBe(colors.danger);
  expect(themeSettings.palette.success).toBe(colors.success);
  expect(themeSettings.palette.warning).toBe(colors.warning);
  expect(themeSettings.palette.info).toBe(colors.info);
});

test("the shell is white with ink text, and the top bar is the canvas", () => {
  expect(themeSettings.palette.layout.sidebarBg).toBe(colors["shell-base"]);
  expect(themeSettings.palette.layout.sidebarText).toBe(colors["on-shell"]);
  expect(themeSettings.palette.layout.navbarBg).toBe(colors.background);
});

test("tables use the neutral surface family, not orange", () => {
  expect(themeSettings.palette.table.headerBg).toBe(colors.surface);
  expect(themeSettings.palette.table.rowBg).toBe(colors["surface-elevated"]);
  expect(themeSettings.palette.table.rowText).toBe(colors["on-surface"]);
});

test("the appearance is bento, dense, and the kit's radius and shadow defaults are kept", () => {
  expect(themeSettings.appearance).toBe("bento");
  expect(themeSettings.density).toBe(0.8);
  // Passing `radius` or `shadow` would replace the bento defaults (24px cards, diffuse shadow) with the kit's plain ones.
  expect("radius" in themeSettings).toBe(false);
  expect("shadow" in themeSettings).toBe(false);
});
