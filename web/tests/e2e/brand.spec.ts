// F0-15 · e2e: the web serves the favicon, the manifest and the PWA icons.
import { test, expect } from "../support/test";

for (const path of ["/favicon.svg", "/favicon.ico", "/site.webmanifest", "/icon-192.png", "/icon-512.png"]) {
  test(`serves ${path}`, async ({ request }) => {
    const response = await request.get(path);
    expect(response.status()).toBe(200);
  });
}
