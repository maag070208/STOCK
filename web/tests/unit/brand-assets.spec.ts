// F0-15 · unit: every icon named in the web manifest exists and has the declared size; the head links the brand icons.
import { test, expect } from "@playwright/test";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, "..", "..", "public");

// PNG width and height live in the IHDR chunk: bytes 16 to 23, big-endian.
function pngSize(path: string): { width: number; height: number } {
  const buffer = readFileSync(path);
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

test("every manifest icon exists and has its declared size", () => {
  const manifest = JSON.parse(readFileSync(join(publicDir, "site.webmanifest"), "utf8"));
  expect(manifest.icons.length).toBeGreaterThan(0);
  for (const icon of manifest.icons) {
    const file = join(publicDir, icon.src);
    expect(existsSync(file), `${icon.src} must exist`).toBe(true);
    const [w, h] = icon.sizes.split("x").map(Number);
    expect(pngSize(file)).toEqual({ width: w, height: h });
  }
});

test("the head links the favicon, the touch icon, the manifest and the theme colors", () => {
  const html = readFileSync(join(here, "..", "..", "index.html"), "utf8");
  expect(html).toContain('rel="icon" href="/favicon.svg"');
  expect(html).toContain('rel="apple-touch-icon" href="/apple-touch-icon.png"');
  expect(html).toContain('rel="manifest" href="/site.webmanifest"');
  expect(html).toContain('name="theme-color" content="#F2F6FC" media="(prefers-color-scheme: light)"');
  expect(html).toContain('name="theme-color" content="#0C1730" media="(prefers-color-scheme: dark)"');
});

test("the glyphs served by the web are exact copies of the brand masters", () => {
  const masters = join(here, "..", "..", "..", "design", "brand", "master");
  for (const name of ["stockflow-glyph-light.svg", "stockflow-glyph-dark.svg"]) {
    expect(readFileSync(join(publicDir, "brand", name)).equals(readFileSync(join(masters, name))), name).toBe(true);
  }
});
