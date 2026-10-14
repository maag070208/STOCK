// F0-19 · unit: the app's interface is built only from the design. No Material components, and no color typed by hand.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const app = join(root, "app");
const read = (path) => readFileSync(join(app, path), "utf8");

function kotlinFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === "build" ? [] : kotlinFiles(path);
    return entry.name.endsWith(".kt") ? [path] : [];
  });
}
const sources = [...kotlinFiles(join(app, "shared/src")), ...kotlinFiles(join(app, "androidApp/src"))];

test("unit: the app does not depend on Material", () => {
  for (const file of ["shared/build.gradle.kts", "androidApp/build.gradle.kts", "gradle/libs.versions.toml"]) {
    assert.doesNotMatch(read(file), /material/i, `${file} must not name a Material artifact`);
  }
  for (const file of sources) {
    assert.doesNotMatch(readFileSync(file, "utf8"), /androidx\.compose\.material/, `${relative(app, file)} imports Material`);
  }
});

test("unit: no color is typed by hand; they all come from the generated tokens", () => {
  assert.ok(sources.length > 0);
  for (const file of sources) {
    if (file.endsWith("ui/theme/DesignTokens.kt")) continue;
    assert.doesNotMatch(readFileSync(file, "utf8"), /0x[0-9A-Fa-f]{6,8}\b|Color\.(Black|White|Red|Green|Blue|Gray|DarkGray|LightGray|Yellow|Cyan|Magenta)\b/,
      `${relative(app, file)} types a color by hand`);
  }
});

test("unit: the Android window opens in the design's workspace color", () => {
  assert.match(read("androidApp/src/main/AndroidManifest.xml"), /android:theme="@style\/Theme\.StockFlow"/);
  for (const folder of ["values", "values-night"]) {
    const theme = read(`androidApp/src/main/res/${folder}/themes.xml`);
    assert.match(theme, /<style name="Theme\.StockFlow"/);
    assert.match(theme, /<item name="android:windowBackground">@color\/design_background<\/item>/);
  }
});
