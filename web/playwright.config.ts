import { defineConfig, devices } from "@playwright/test";

// Unit tests run without a browser; E2E tests drive the app through the dev server.
export default defineConfig({
  testDir: "tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // A test that fails on the first run is not retried locally (the suite is fast enough to run again).
  reporter: "list",
  // What the API cannot remove (a closed invoice, the append-only movements, a killed run) is taken away when the suite
  // is over, so the database the tests fill is left clean every time (testing.md rule 4).
  globalTeardown: "./tests/support/teardown.ts",
  // Three at a time, not more: the tests share one database that grows as they run, and past three they slow each other
  // into timeouts that have nothing to do with the app.
  workers: process.env.CI ? 1 : 3,
  // What a person waits for an element the app is fetching from that database.
  expect: { timeout: 10_000 },
  use: { baseURL: "http://localhost:5173", trace: "retain-on-failure" },
  webServer: {
    command: "pnpm dev --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
  },
  projects: [
    { name: "unit", testMatch: /unit\/.*\.spec\.ts/, use: { baseURL: undefined } },
    // An end-to-end test builds its own data through the API and drives the app. 30 s, and it fails fast: a suite nobody
    // waits for is a suite nobody runs (testing.md).
    { name: "e2e", testMatch: /e2e\/.*\.spec\.ts/, timeout: 30_000, use: { ...devices["Desktop Chrome"] } },
  ],
});
