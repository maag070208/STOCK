// F1-20 · unit: the app's brand glyph is the master's artwork in another format, never redrawn (brand.md rule 1).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { outputs, readGlyph } from "../../scripts/brand-vectors.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

test("unit: every path, color and stroke of the master glyph is in the app's vector", () => {
  for (const variant of ["light", "dark"]) {
    const svg = readFileSync(join(root, `design/brand/master/stockflow-glyph-${variant}.svg`), "utf8");
    const vector = readFileSync(join(root, `app/shared/src/commonMain/composeResources/drawable/stockflow_glyph_${variant}.xml`), "utf8");
    const shapes = readGlyph(svg);
    assert.equal(shapes.length, 12, variant);
    for (const shape of shapes) {
      assert.ok(vector.includes(`android:pathData="${shape.d}"`), `${variant}: ${shape.d}`);
      for (const color of [shape.fill, shape.stroke].filter((c) => c && c !== "none")) assert.ok(vector.includes(`"${color}"`), `${variant}: ${color}`);
    }
    for (const [, path] of svg.matchAll(/ d="([^"]+)"/g)) assert.ok(vector.includes(path), `${variant}: ${path}`);
  }
});

test("unit: the app's glyph vectors are up to date with the brand masters", () => {
  for (const [path, content] of Object.entries(outputs())) {
    assert.equal(readFileSync(join(root, path), "utf8"), content, `${path} is stale: run node scripts/brand-vectors.mjs`);
  }
});
