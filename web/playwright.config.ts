import { defineConfig, devices } from "@playwright/test";

// Unit tests run without a browser; E2E tests drive the app through the dev server.
export default defineConfig({
  testDir: "tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "list",
  use: { baseURL: "http://localhost:5173", trace: "retain-on-failure" },
  webServer: {
    command: "pnpm dev --port 5173 --strictPort",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
  },
  projects: [
    { name: "unit", testMatch: /unit\/.*\.spec\.ts/, use: { baseURL: undefined } },
    // An end-to-end test builds its own data through the API and drives the app: the default 30 s is tight when the
    // whole suite runs at once, and a heavy fixture is not a slow screen (testing.md).
    { name: "e2e", testMatch: /e2e\/.*\.spec\.ts/, timeout: 60_000, use: { ...devices["Desktop Chrome"] } },
  ],
});
