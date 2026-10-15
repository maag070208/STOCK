// The test the suites use: Playwright's, with the cleanup of what the test created. Every e2e spec imports `test` from
// here instead of from `@playwright/test`, so nothing a test wrote outlives it (testing.md rule 4).
import { test as base, expect } from "@playwright/test";
import { createdMark, drainCreated } from "./api";

export const test = base.extend<{ cleanup: void }>({
	// Automatic: it runs for every test, whether it passed or failed.
	cleanup: [
		async ({}, use) => {
			const before = createdMark();
			await use();
			await drainCreated(before);
		},
		{ auto: true },
	],
});

export { expect };
export type { Page } from "@playwright/test";
