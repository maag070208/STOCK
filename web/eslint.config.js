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
);
