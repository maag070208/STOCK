// F5-14 · unit: docs/i18n/documents.md is the single source of the API's document labels.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { readLabels, outputs } from "../../scripts/document-messages.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const rows = readLabels(join(root, "docs/i18n/documents.md"));

test("unit: every document label has a key and both languages, and no key repeats", () => {
  assert.ok(rows.length > 10);
  assert.equal(new Set(rows.map((row) => row.key)).size, rows.length);
  for (const row of rows) {
    assert.match(row.key, /^[a-z_]+(\.[a-z_]+)+$/, row.key);
    assert.ok(row.es && row.en, row.key);
  }
});

test("unit: the label files are up to date with the document", () => {
  for (const [path, content] of Object.entries(outputs(rows))) {
    assert.equal(readFileSync(join(root, path), "utf8"), content, `${path} is stale: run node scripts/document-messages.mjs`);
  }
});

// The Kotlin side reads the labels by key: a key it asks for that the document does not have fails here.
test("unit: the labels the API prints are in the document", () => {
  const source = readFileSync(join(root, "api/src/main/kotlin/com/axzydev/stock/api/modules/orders/OrderDocumentText.kt"), "utf8");
  const wanted = [...source.matchAll(/get\("([a-z_.]+)"/g)].map((match) => match[1]);
  assert.ok(wanted.length > 10, "the labels are read from the source");
  const keys = new Set(rows.map((row) => row.key));
  assert.deepEqual(wanted.filter((key) => !keys.has(key)), []);
});
