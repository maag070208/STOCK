// Layer rules from docs/architecture/web.md section 2. A forbidden import fails the lint.
import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import boundaries from "eslint-plugin-boundaries";

const all = ["app", "shared", "entities", "features", "widgets", "pages"];

// Each layer may import from the layers listed here, and from nothing else.
const allowFrom = (from, to) => ({
  from: { element: { type: from } },
  allow: { to: { element: { types: { anyOf: to } } } },
});

export default tseslint.config(
  { ignores: ["dist", "playwright-report", "test-results"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // Only the kit builds a screen (docs/architecture/web.md section 8): raw HTML in the app's own screens is a defect,
    // and this is what keeps it from coming back. `form` is the one element a screen may write.
    files: ["src/pages/**/*.tsx", "src/features/**/*.tsx", "src/entities/**/*.tsx", "src/widgets/**/*.tsx"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector:
            "JSXOpeningElement[name.name=/^(div|span|p|dl|dt|dd|table|thead|tbody|tfoot|tr|th|td|ul|ol|li|h[1-6]|section|article|header|footer|nav|aside|main)$/]",
          message:
            "A screen is built with the UI kit, not with raw HTML (web.md section 8): use ITStack, ITFlex, ITGrid, ITText, ITTable, ITCard, ITStatCard or ITField.",
        },
      ],
    },
  },
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: { globals: globals.browser },
    plugins: { "react-hooks": reactHooks, boundaries },
    settings: {
      // Resolves .ts and .tsx imports, so layer rules see every import.
      "import/resolver": { typescript: true, node: true },
      "boundaries/elements": [
        { type: "app", pattern: "src/app/**" },
        { type: "shared", pattern: "src/shared/**" },
        { type: "entities", pattern: "src/entities/**" },
        { type: "features", pattern: "src/features/**" },
        { type: "widgets", pattern: "src/widgets/**" },
        { type: "pages", pattern: "src/pages/**" },
        // Fixtures for the layer-rule tests: each folder is classified as shared.
        { type: "shared", pattern: "tests/fixtures/forbidden/**" },
        { type: "shared", pattern: "tests/fixtures/allowed/**" },
      ],
    },
    rules: {
      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          policies: [
            allowFrom("app", all),
            allowFrom("pages", all),
            allowFrom("widgets", ["app", "features", "entities", "shared"]),
            allowFrom("features", ["app", "entities", "shared"]),
            allowFrom("entities", ["entities", "shared"]),
            allowFrom("shared", ["shared"]),
          ],
        },
      ],
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
    },
  },
  {
    // A screen is built from the UI kit: no markup of our own (web.md section 8). The rule covers the screens that are
    // converted already — the invoices, first — and grows with each one, so the rest keep working while they wait.
    files: ["src/pages/invoices/**/*.tsx", "src/features/invoice/**/*.tsx"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "JSXOpeningElement[name.name=/^(div|span|p|h[1-6]|table|thead|tbody|tr|td|th|dl|dt|dd|ul|ol|li)$/]",
          message: "Use the UI kit (ITStack, ITFlex, ITGrid, ITText, ITTable…) instead of markup of our own: web.md section 8.",
        },
      ],
    },
  },
);
