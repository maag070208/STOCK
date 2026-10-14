// F0-01 · Repository and local environment
// Unit: the compose file names the project `stockflow` and declares a healthy database.
// E2E: `docker compose up` brings PostgreSQL to healthy.
import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const compose = (args) =>
  execFileSync("docker", ["compose", ...args], { cwd: root, encoding: "utf8" });

test("unit: compose project is named stockflow and db has a healthcheck", () => {
  const cfg = JSON.parse(compose(["config", "--format", "json"]));
  assert.equal(cfg.name, "stockflow");
  assert.ok(cfg.services.db, "service db must exist");
  assert.ok(cfg.services.db.healthcheck, "db must declare a healthcheck");
});

test("unit: the API service builds from api/, waits for the database and declares a readiness check", () => {
  const cfg = JSON.parse(compose(["config", "--format", "json"]));
  const api = cfg.services.api;
  assert.ok(api, "service api must exist");
  assert.ok(api.build.context.endsWith("/api"), "api must build from the api folder");
  assert.equal(api.depends_on.db.condition, "service_healthy");
  assert.equal(api.environment.DB_URL, "jdbc:postgresql://db:5432/stockflow");
  assert.match(api.healthcheck.test.join(" "), /\/api\/v1\/health\/ready/);
});

test("e2e: docker compose up brings PostgreSQL to healthy", () => {
  compose(["up", "-d", "--wait", "db"]);
  const status = execFileSync(
    "docker", ["compose", "ps", "--format", "json", "db"], { cwd: root, encoding: "utf8" });
  assert.match(status, /"Health":"healthy"/);
});

// The API takes its own host port here, so the test does not collide with an API started from Gradle on 8080.
test("e2e: docker compose up starts the API, and it reports ready", { timeout: 900000 }, async () => {
  const env = { ...process.env, API_PORT: "18080" };
  try {
    execFileSync("docker", ["compose", "up", "-d", "--build", "--wait", "api"], { cwd: root, env, stdio: "pipe" });
    const response = await fetch("http://localhost:18080/api/v1/health/ready");
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { status: "READY" });
  } finally {
    execFileSync("docker", ["compose", "rm", "--stop", "--force", "api"], { cwd: root, env, stdio: "pipe" });
  }
});
