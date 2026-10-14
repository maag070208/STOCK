// F0-05 · unit: docs/i18n/messages.md is the single source of the API's error messages.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { readMessages, outputs } from "../../scripts/error-messages.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const rows = readMessages(join(root, "docs/i18n/messages.md"));

test("unit: every row has a code and both messages, and no code repeats", () => {
  assert.ok(rows.length > 30);
  assert.equal(new Set(rows.map((row) => row.code)).size, rows.length);
  for (const row of rows) assert.ok(row.es && row.en, row.code);
});

test("unit: a placeholder becomes a MessageFormat index, in both languages", () => {
  const files = outputs(rows);
  assert.ok(files["api/src/main/resources/messages/errors_es.properties"].includes("error.stock_insufficient=No hay existencias suficientes. Disponibles: {0}."));
  assert.ok(files["api/src/main/resources/messages/errors_en.properties"].includes("error.stock_insufficient=Not enough stock. Available: {0}."));
});

test("unit: the message files are up to date with the document", () => {
  for (const [path, content] of Object.entries(outputs(rows))) {
    assert.equal(readFileSync(join(root, path), "utf8"), content, `${path} is stale: run node scripts/error-messages.mjs`);
  }
});

test("unit: the document and the API's error codes are the same list", () => {
  const source = readFileSync(join(root, "api/src/main/kotlin/com/axzydev/stock/api/core/errors/ErrorCode.kt"), "utf8");
  const codes = [...source.matchAll(/^\s+([A-Z_]+)\(\d+\)[,;]/gm)].map((match) => match[1]);
  assert.deepEqual([...codes].sort(), rows.map((row) => row.code).sort());
});
