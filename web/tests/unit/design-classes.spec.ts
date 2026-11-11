// F0-18 · unit: the app's utilities carry the prefix "sf". A class without it would be the Axzy UI System's, or nothing at all.
import { test, expect } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const src = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "src");
// Plain class names that are hooks for app/index.css, not utilities.
const hooks = new Set([
  "sf-shell",
  "sf-on-primary",
  "sf-rise",
  "sf-stats",
  "sf-art-scene",
  "sf-art-carton-a",
  "sf-art-carton-b",
  "sf-art-carton-c",
  "sf-art-label",
  "sf-art-check",
  "sf-art-scan",
  "sf-art-ring",
  "sf-art-dot",
]);

function components(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return components(path);
    return entry.name.endsWith(".tsx") ? [path] : [];
  });
}

test("every class written in the app starts with sf:", () => {
  const unprefixed: string[] = [];
  let classes = 0;
  for (const file of components(src)) {
    const source = readFileSync(file, "utf8");
    for (const match of source.matchAll(/[cC]lassName="([^"]*)"/g)) {
      for (const name of match[1].split(/\s+/).filter(Boolean)) {
        classes += 1;
        if (!name.startsWith("sf:") && !hooks.has(name)) unprefixed.push(`${file.slice(src.length + 1)}: ${name}`);
      }
    }
  }
  expect(classes).toBeGreaterThan(0);
  expect(unprefixed).toEqual([]);
});

test("the stylesheet sets the prefix and puts the app's utilities above the kit's layer", () => {
  const css = readFileSync(join(src, "app", "index.css"), "utf8");
  expect(css).toContain('@import "tailwindcss" prefix(sf);');
  expect(css).toContain("@layer theme, base, axzy-ui-system, components, utilities;");
  // The kit ships compiled: the app does not rebuild its utilities.
  expect(css).not.toContain("@source");
});
