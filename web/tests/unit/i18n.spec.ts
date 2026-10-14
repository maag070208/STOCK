// F0-11 · unit: every key exists in both es and en, in every namespace, and es is the default locale.
import { test, expect } from "@playwright/test";
import { resources, defaultLocale, supportedLocales, namespaces } from "../../src/shared/i18n/resources";

function keysOf(value: unknown, prefix = ""): string[] {
  if (value === null || typeof value !== "object") return [prefix];
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    keysOf(child, prefix ? `${prefix}.${key}` : key),
  );
}

test("the default locale is Spanish and both locales are supported", () => {
  expect(defaultLocale).toBe("es");
  expect(supportedLocales).toEqual(["es", "en"]);
});

for (const namespace of namespaces) {
  test(`every key of ${namespace} in es exists in en, and the reverse`, () => {
    const es = keysOf(resources.es[namespace]).sort();
    const en = keysOf(resources.en[namespace]).sort();
    expect(en).toEqual(es);
    expect(es.length).toBeGreaterThan(0);
  });
}

test("placeholders match across locales", () => {
  for (const namespace of namespaces) {
    const placeholders = (locale: "es" | "en") =>
      keysOf(resources[locale][namespace]).map((key) => {
        const value = key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], resources[locale][namespace]);
        return [key, String(value).match(/{{\w+}}/g)?.sort().join() ?? ""];
      });
    expect(placeholders("en")).toEqual(placeholders("es"));
  }
});

// Every permission and audit action of the API has its labels: tests/ops/web-labels.test.mjs reads them from the API's code.
