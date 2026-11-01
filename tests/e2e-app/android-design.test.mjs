// F0-19 · e2e: the Android app is drawn in the bento design (design/stockflow-DESIGN.md): a soft gray canvas, white
// cards, pastel tiles and the orange accent. The expected colors are read from the document and compared with the
// pixels of the screen. The entry illustration and the figures of the home carry their own animation, so the checks
// read the surface behind a label, never the glyph of the moment.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { adb, bounds, darkMode, isEmulator, launch, screenshot, scrollTo, signIn, sleep, waitForText } from "./lib/android.mjs";
import { newUser } from "./lib/api.mjs";
import { colors, distance, hasArtwork, rgb, workspace } from "./lib/design.mjs";

function assertColor(actual, expected, tolerance, what) {
  assert.ok(distance(actual, expected) <= tolerance, `${what}: expected about rgb(${expected}), the screen shows rgb(${actual})`);
}

// Text is anti-aliased, so its color is the pixel inside its bounds that comes closest.
function closestInside(shot, box, expected) {
  let best = [255, 255, 255];
  let bestOff = Infinity;
  for (let y = box.top; y < box.bottom; y++) {
    for (let x = box.left; x < box.right; x++) {
      const pixel = shot.at(x, y);
      const off = distance(pixel, expected);
      if (off < bestOff) [best, bestOff] = [pixel, off];
    }
  }
  return best;
}

// The tones of the bento tiles, from the design document. Dark mode has its own pair for each tone.
const tone = (name) => ({ light: rgb(colors[`tone-${name}-bg`]), dark: rgb(colors[`dark-tone-${name}-bg`]) });
const ORANGE = tone("orange");
const INK = tone("ink");

async function checkSignIn() {
  const xml = await waitForText("Iniciar sesión");
  const shot = screenshot();
  const expected = workspace(darkMode() ? "dark" : "light");

  // The entry screen is the canvas, with the illustration above the form.
  assertColor(shot.at(4, 4), expected.canvas, 1, "status bar strip");
  assert.ok(hasArtwork(shot, expected.canvas), "the entry screen must show the brand illustration");
  assertColor(closestInside(shot, bounds(xml, "StockFlow"), expected.title), expected.title, 6, "brand name");

  // The title, and the one primary control: the orange pill with white text.
  const title = bounds(xml, "Iniciar sesión");
  assertColor(closestInside(shot, title, rgb(colors["on-surface"])), rgb(colors["on-surface"]), 6, "entry title");
  const button = bounds(xml, "Entrar");
  assertColor(shot.at(button.left - 16, button.centerY), rgb(colors.primary), 1, "sign-in button");
  assertColor(closestInside(shot, button, rgb(colors["on-primary"])), rgb(colors["on-primary"]), 6, "sign-in button text");
}

async function checkHome(mode) {
  const expected = workspace(mode);
  const user = await newUser(["WAREHOUSE_OPERATOR"]);
  await launch({ fresh: true });
  await checkSignIn();
  await signIn(user.username, user.password);
  const xml = await waitForText("Conectado a la API");
  // The tiles rise into place in their first moments; the colors are read once they stand.
  await sleep(900);
  const shot = screenshot();

  // The canvas, the greeting in ink, and the two tasks of the receipt on their pastel tiles.
  assertColor(shot.at(4, 4), expected.canvas, 1, "status bar strip");
  const greeting = bounds(xml, "Hola, Persona de Prueba");
  assertColor(closestInside(shot, greeting, expected.title), expected.title, 6, "greeting");

  const count = bounds(xml, "Contar");
  assertColor(shot.at(Math.max(4, count.left - 16), count.centerY), ORANGE[mode], 2, "count tile");
  const place = bounds(xml, "Ubicar");
  assertColor(shot.at(Math.max(4, place.left - 16), place.centerY), INK[mode], 2, "placement tile");

  // The state of the API is the pale green of a healthy service, in both modes.
  const badge = bounds(xml, "Conectado a la API");
  assertColor(shot.at(badge.left + 6, badge.top + 3), rgb(colors["status-production-bg"]), 1, "badge fill");
  assertColor(closestInside(shot, badge, rgb(colors["status-production-fg"])), rgb(colors["status-production-fg"]), 6, "badge text");

  // The modules are lower on the screen: white cards in light mode and the dark surface in dark mode.
  const lower = await scrollTo("module-orders");
  const orders = bounds(lower, "Órdenes");
  const card = mode === "dark" ? rgb(colors["dark-surface"]) : rgb(colors.surface);
  assertColor(screenshot().at(Math.max(4, orders.left - 16), orders.centerY), card, 2, "module card");
}

// An emulator is switched to each mode. A phone is checked in the mode it is in, and its setting is not touched.
const nightBefore = /Night mode: (\w+)/.exec(adb("shell", "cmd", "uimode", "night"))[1];
if (isEmulator) after(() => adb("shell", "cmd", "uimode", "night", nightBefore));

for (const mode of isEmulator ? ["light", "dark"] : [darkMode() ? "dark" : "light"]) {
  test(`e2e: in ${mode} mode the home is the bento dashboard over the design's canvas`, async () => {
    if (isEmulator) adb("shell", "cmd", "uimode", "night", mode === "dark" ? "yes" : "no");
    await checkHome(mode);
  });
}
