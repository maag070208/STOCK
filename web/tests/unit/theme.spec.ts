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

test("tables use the neutral surface family, not orange", () => {
  expect(themeSettings.palette.table.headerBg).toBe(colors.surface);
  expect(themeSettings.palette.table.rowBg).toBe(colors["surface-elevated"]);
  expect(themeSettings.palette.table.rowText).toBe(colors["on-surface-muted"]);
});

test("the shell is flat with subtle shadows, and the kit's single radius is not used", () => {
  expect(themeSettings.appearance).toBe("flat");
  expect(themeSettings.shadow).toBe(1);
  // The kit's radius prop sets one value for every corner. The design has four, so it stays unset.
  expect("radius" in themeSettings).toBe(false);
});
