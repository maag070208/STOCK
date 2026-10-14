// The web shows every permission and every audit action by name, in Spanish and in English.
// The API's code is the source of both lists, so a new permission or action without its labels fails here.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const kotlin = join(root, "api/src/main/kotlin");
const locales = ["es", "en"];
const label = (locale, namespace) => JSON.parse(readFileSync(join(root, `web/src/shared/i18n/locales/${locale}/${namespace}.json`), "utf8"));

function sources(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? sources(path) : path.endsWith(".kt") ? [readFileSync(path, "utf8")] : [];
  });
}

// The permission keys of PermissionCatalog: the literal in each definition, or the constant it names.
export function permissionKeys(source) {
  const constants = Object.fromEntries([...source.matchAll(/const val (\w+) = "([a-z_]+\.[a-z_.]+)"/g)].map((m) => [m[1], m[2]]));
  return [...source.matchAll(/(?:adminOnly|PermissionDef)\(\s*(?:"([a-z_]+\.[a-z_.]+)"|(\w+))/g)]
    .map((m) => m[1] ?? constants[m[2]])
    .filter(Boolean);
}

// The audit actions and target types: every audit.record(actor, "ACTION", "target", ...) in the code, and the denial.
export function auditEntries(texts) {
  const actions = new Set(), targets = new Set();
  for (const text of texts) {
    for (const m of text.matchAll(/audit\.record\(\s*[^,]+,\s*(?:"([A-Z_]+)"|(\w+)),\s*"([a-z_]+)"/g)) {
      if (m[1]) actions.add(m[1]);
      targets.add(m[3]);
    }
    for (const m of text.matchAll(/const val ACTION = "([A-Z_]+)"/g)) actions.add(m[1]);
  }
  return { actions: [...actions].sort(), targets: [...targets].sort() };
}

const at = (node, key) => key.split(".").reduce((value, part) => value?.[part], node);

test("unit: permission keys come from literals and from constants", () => {
  const source = `const val ROLES_MANAGE = "roles.manage"\nadminOnly("users.view"),\nadminOnly(ROLES_MANAGE, sensitive = true),\nPermissionDef(\n"locations.view", listOf())`;
  assert.deepEqual(permissionKeys(source), ["users.view", "roles.manage", "locations.view"]);
});

test("unit: audit entries are read from each record call", () => {
  const found = auditEntries([`audit.record(actor.actor, "USER_CREATED", "user", id)\naudit.record(actor, ACTION, "access", denied)\nconst val ACTION = "ACCESS_DENIED"`]);
  assert.deepEqual(found, { actions: ["ACCESS_DENIED", "USER_CREATED"], targets: ["access", "user"] });
});

test("every permission of the API has a name in both languages", () => {
  const keys = permissionKeys(readFileSync(join(kotlin, "com/axzydev/stock/api/modules/identity/access/PermissionCatalog.kt"), "utf8"));
  assert.ok(keys.includes("roles.manage") && keys.includes("locations.view"), "the catalog is read");
  for (const locale of locales) {
    const names = label(locale, "common").permissions;
    const missing = keys.filter((key) => typeof at(names, key) !== "string");
    assert.deepEqual(missing, [], `${locale}: permissions without a name`);
  }
});

test("every audit action and target of the API has a label in both languages", () => {
  const { actions, targets } = auditEntries(sources(kotlin));
  assert.ok(actions.includes("USER_CREATED") && actions.includes("ACCESS_DENIED"), "the actions are read");
  for (const locale of locales) {
    const audit = label(locale, "audit");
    assert.deepEqual(actions.filter((action) => typeof audit.actions[action] !== "string"), [], `${locale}: actions without a label`);
    assert.deepEqual(targets.filter((target) => typeof audit.targets[target] !== "string"), [], `${locale}: targets without a label`);
  }
});
