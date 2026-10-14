// F0-12 · e2e: the Android app launches and, with no saved session, shows the sign-in screen.
// Uses adb and the accessibility tree, so it does not depend on the test library that does not yet support Android 17.
import { test } from "node:test";
import assert from "node:assert/strict";
import { launch, waitUntil } from "./lib/android.mjs";

test("e2e: the Android app launches and shows the sign-in screen", async () => {
  await launch({ fresh: true });
  // The brand intro plays for about 4.3 seconds first (F0-16).
  const screen = await waitUntil((xml) => xml.includes('text="Iniciar sesión"') && !xml.includes("Intro de StockFlow"), 20000);
  assert.ok(screen, "the sign-in screen must follow the intro");
  assert.match(screen, /text="StockFlow"/);
});
