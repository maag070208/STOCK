import { expect, type Page } from "@playwright/test";

// The forms of the app: what a test needs to fill a form whose lists are searched instead of scrolled.

// The floating panel of a search select. The kit portals it to the body and marks it with `data-it-floating`; its
// options live there and nowhere else in the page.
export const SEARCH_PANEL = '[data-it-floating="true"]';

// The panel that holds an option, which is the one the person is looking at when two search selects are open at once.
function panelWith(page: Page, option: string | RegExp) {
	return page.locator(SEARCH_PANEL).filter({ has: page.getByText(option) }).first();
}

// Picks an option of a search select: what the person does, typing enough of the label and clicking it.
export async function pickSearchOption(page: Page, field: string, term: string, option: string | RegExp) {
	await page.locator(`input[name="${field}"]`).fill(term);
	const chosen = panelWith(page, option).getByText(option).first();
	await expect(chosen).toBeVisible();
	await chosen.click();
}

// The same, for a picker that searches the API: it waits for the option the term names, which arrives from the server.
export async function pickRemoteOption(page: Page, field: string, term: string, option: string | RegExp) {
	await page.locator(`input[name="${field}"]`).fill(term);
	const chosen = panelWith(page, option).getByText(option).first();
	try {
		await expect(chosen).toBeVisible({ timeout: 10_000 });
	} catch (error) {
		const panels = await page.locator(SEARCH_PANEL).allInnerTexts();
		console.log(`[debug] campo=${field} término=${term} paneles=${JSON.stringify(panels)}`);
		throw error;
	}
	await chosen.click();
}
