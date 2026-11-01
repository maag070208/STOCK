// F0-16 · e2e: the intro plays once when the app opens, then steps aside; with "remove animations" it holds one still frame and steps aside.
// The intro is recognized on the screen's pixels: while an app starts, the accessibility tree cannot be read.
import { test } from "node:test";
import assert from "node:assert/strict";
import { adb, darkMode, isEmulator, launch, screenshot, sleep, waitUntil } from "./lib/android.mjs";
import { hasArtwork, workspace } from "./lib/design.mjs";

const animationScale = () => adb("shell", "settings", "get", "global", "animator_duration_scale").trim();
const setAnimationScale = (value) => adb("shell", "settings", "put", "global", "animator_duration_scale", value);

// What the screen shows now: the artwork of the brand over the canvas. Both the intro and the entry screen carry it,
// so the screen after it is recognized by the form's text, not by the pixels.
function artworkNow() {
  const canvas = workspace(darkMode() ? "dark" : "light").canvas;
  return hasArtwork(screenshot(), canvas);
}

async function waitForArtwork(limitMs) {
  const deadline = Date.now() + limitMs;
  while (Date.now() < deadline) {
    if (artworkNow()) return true;
    await sleep(100);
  }
  return false;
}

test("e2e: the brand intro plays when the app opens, then the sign-in screen is shown", async (t) => {
  const before = animationScale();
  // An emulator is set to play animations. A phone keeps its setting, and the test needs animations on.
  if (isEmulator) setAnimationScale("1");
  else if (Number(before) === 0) return t.skip("the device has animations removed");
  try {
    await launch({ fresh: true });
    assert.ok(await waitForArtwork(10000), "the brand intro must be on screen when the app opens");
    assert.ok(await waitUntil((xml) => xml.includes('text="Iniciar sesión"'), 12000), "the sign-in screen must follow the intro");
  } finally {
    if (isEmulator) setAnimationScale(before === "null" ? "1" : before);
  }
});

test("e2e: with reduced motion the intro holds a still frame for a moment and steps aside", { skip: !isEmulator && "changes the device's animation setting" }, async () => {
  const before = animationScale();
  setAnimationScale("0");
  try {
    await launch({ fresh: true });
    // The still frame lasts one second, too short to be sure of catching it; it is checked by hand (see the task).
    // What the test can see: the intro never blocks sign-in.
    assert.ok(await waitUntil((xml) => xml.includes('text="Iniciar sesión"'), 15000), "sign-in must show without waiting on the intro");
  } finally {
    setAnimationScale(before === "null" ? "1" : before);
  }
});
