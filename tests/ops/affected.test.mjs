// F0-14 · unit: the script picks only the packages whose files changed.
// e2e: a change to web/ runs the web suite and not the API suite (through the CLI the pipeline uses).
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { affectedPackages } from "../../scripts/affected.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

test("unit: a web file marks only the web package", () => {
  assert.deepEqual(affectedPackages(["web/src/app/App.tsx"]), ["web"]);
});

test("unit: api, app and web changes mark their own packages", () => {
  assert.deepEqual(
    affectedPackages(["web/src/app/App.tsx", "app/shared/src/commonMain/B.kt"]),
    ["web", "app"],
  );
});

// The web's end-to-end suite runs against the API, so an API change can break it.
test("unit: an api change marks the web package too", () => {
  assert.deepEqual(affectedPackages(["api/src/main/kotlin/A.kt"]), ["api", "web"]);
});

test("unit: docs and test scripts mark no package", () => {
  assert.deepEqual(affectedPackages(["docs/phases/F0-foundations.md", "tests/ops/affected.test.mjs"]), []);
});

test("unit: a root compose change marks every package", () => {
  assert.deepEqual(affectedPackages(["docker-compose.yml"]), ["api", "web", "app"]);
});

test("e2e: the CLI runs the web suite and not the API suite for a web change", () => {
  const out = execFileSync("node", [join(root, "scripts/affected.mjs"), "--files", "web/src/app/App.tsx"], { encoding: "utf8" });
  assert.equal(out.trim(), "web");
});
