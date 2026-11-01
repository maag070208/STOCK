// F0-17 · unit: the design document is the single source of the tokens for web and app.
// The generated files must match what the script produces from design/stockflow-DESIGN.md.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { readDesign, outputs } from "../../scripts/design-tokens.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const design = readDesign(join(root, "design/stockflow-DESIGN.md"));

test("unit: the design document yields its colors, shapes and shell measures", () => {
  assert.equal(Object.keys(design.colors).length, 55);
  assert.equal(design.colors.primary, "#fe6e00");
  assert.equal(design.colors.background, "#f4f4f6");
  assert.equal(design.colors["on-surface-muted"], "#64748b");
  assert.deepEqual(design.rounded, { xs: "4px", sm: "6px", md: "8px", lg: "12px", xl: "16px", "2xl": "20px", "3xl": "24px", pill: "9999px" });
  assert.equal(design.spacing["2xl"], "32px");
  assert.equal(design.layout["shell-sidebar-expanded"], "248px");
  assert.equal(design.elevation["shell-opacity"], "0.70");
  assert.equal(design.typography["headline-lg"].fontWeight, 800);
  assert.equal(design.typography["label-sm"].letterSpacing, "0.05em");
});

test("unit: every generated token file is up to date with the design document", () => {
  for (const [path, content] of Object.entries(outputs(design))) {
    assert.equal(readFileSync(join(root, path), "utf8"), content, `${path} is stale: run node scripts/design-tokens.mjs`);
  }
});

test("unit: the generator writes the web tokens, the web theme, the app tokens and the Android window colors", () => {
  assert.deepEqual(Object.keys(outputs(design)).sort(), [
    "app/androidApp/src/main/res/values-night/design_colors.xml",
    "app/androidApp/src/main/res/values/design_colors.xml",
    "app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/theme/DesignTokens.kt",
    "web/src/app/design-tokens.css",
    "web/src/shared/ui/design-tokens.ts",
  ]);
});

const files = outputs(design);
const kotlin = files["app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/theme/DesignTokens.kt"];
const css = files["web/src/app/design-tokens.css"];

test("unit: the web theme is the design's only: Tailwind's own palette, radii, shadows and type are cleared", () => {
  for (const namespace of ["color", "radius", "shadow", "font", "text"]) assert.ok(css.includes(`--${namespace}-*: initial;`), namespace);
  assert.ok(css.includes("--radius-md: 8px;"));
  assert.ok(css.includes("--text-headline-lg--font-weight: 800;"));
});

test("unit: the app gets each type style in sp, with a unitless line height turned into a size", () => {
  assert.ok(kotlin.includes("val headlineLg = TypeStyle(fontSize = 24f, fontWeight = 800, lineHeight = 32f, letterSpacingEm = -0.02f, monospace = false)"));
  assert.ok(kotlin.includes("val displayHero = TypeStyle(fontSize = 72f, fontWeight = 800, lineHeight = 72f, letterSpacingEm = -0.03f, monospace = false)"));
  assert.ok(kotlin.includes("val codeSm = TypeStyle(fontSize = 14f, fontWeight = 400, lineHeight = 20f, letterSpacingEm = 0f, monospace = true)"));
});

test("unit: the app gets each component with its references resolved to values", () => {
  // "0 16px" is no vertical padding and 16 horizontal.
  assert.ok(kotlin.includes("val shellHeader = Component(backgroundColor = 0xFFF4F4F6L, textColor = 0xFF14171CL, rounded = 0, height = 64, paddingVertical = 0, paddingHorizontal = 16)"));
  assert.ok(kotlin.includes("val cardPanel = Component(backgroundColor = 0xFFFFFFFFL, textColor = 0xFF1F2937L, rounded = 24, paddingVertical = 24, paddingHorizontal = 24)"));
  assert.ok(kotlin.includes("val statusBadgeProduction = Component(backgroundColor = 0xFFDCFCE7L, textColor = 0xFF166534L, rounded = 9999, paddingVertical = 4, paddingHorizontal = 10)"));
});

test("unit: dark mode replaces the same workspace roles on the web and in the app", () => {
  assert.ok(css.includes(".dark {"));
  assert.ok(css.includes("--sf-color-background: var(--sf-color-dark-background);"));
  assert.ok(css.includes("--sf-color-surface-elevated: var(--sf-color-dark-surface);"));
  assert.ok(css.includes("--sf-color-on-surface-muted: var(--sf-color-dark-on-surface-muted);"));
  assert.ok(css.includes("--sf-color-outline: var(--sf-shell-border);"));
  // A part marked data-light keeps the light colors in dark mode.
  assert.ok(css.includes(".dark [data-light] {\n  --sf-color-background: #f4f4f6;"));
  assert.ok(css.includes("--sf-color-surface-elevated: #ffffff;"));
  assert.ok(kotlin.includes("object DarkColors {"));
  assert.ok(kotlin.includes("const val background = 0xFF0E1116L"));
  assert.ok(kotlin.includes("const val surfaceElevated = 0xFF1A1D24L"));
  // The shell's border at 10%.
  assert.ok(kotlin.includes("const val outline = 0x1AE5E7EBL"));
});

test("unit: the Android window opens in the workspace color, light and dark", () => {
  assert.ok(files["app/androidApp/src/main/res/values/design_colors.xml"].includes('<color name="design_background">#F4F4F6</color>'));
  assert.ok(files["app/androidApp/src/main/res/values-night/design_colors.xml"].includes('<color name="design_background">#0E1116</color>'));
});
