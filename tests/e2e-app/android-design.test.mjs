// F0-19 · e2e: the Android app is drawn in the colors of design/command-center-DESIGN.md, in the layout of the
// reference the owner chose on 2026-10-14 (design-system.md section 7).
// The expected colors are read from the document, and compared with the pixels of the screen.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { adb, bounds, darkMode, isEmulator, launch, screenshot, signIn, waitForText } from "./lib/android.mjs";
import { newUser } from "./lib/api.mjs";
import { colors, distance, rgb, workspace } from "./lib/design.mjs";

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

async function checkHome(mode) {
  const expected = workspace(mode);
  const user = await newUser(["WAREHOUSE_OPERATOR"]);
  await launch({ fresh: true });
  await checkSignIn();
  await signIn(user.username, user.password);
  const xml = await waitForText("Conectado a la API");
  const shot = screenshot();
  const orange = rgb(colors.primary);
  const white = rgb(colors["on-primary"]);

  // Hero: the strip behind the status bar and the hero are the brand's orange in both modes, and the greeting is white.
  assertColor(shot.at(4, 4), orange, 1, "status bar strip");
  const greeting = bounds(xml, "Hola, Persona de Prueba");
  assertColor(shot.at(4, greeting.centerY), orange, 1, "hero");
  assertColor(closestInside(shot, bounds(xml, "StockFlow"), white), white, 3, "brand name");

  // Sheet: the workspace canvas of the mode, with the orange pill button and the card.
  assertColor(shot.at(Math.round(shot.width / 2), Math.round(shot.height * 0.85)), expected.canvas, 1, "sheet");
  const button = bounds(xml, "Buscar material");
  assertColor(shot.at(Math.round(shot.width / 2), button.top - 4), orange, 1, "lookup button");
  assertColor(closestInside(shot, button, white), white, 3, "lookup button text");
  const label = bounds(xml, "ESTADO DE LA API");
  assertColor(closestInside(shot, label, expected.muted), expected.muted, 6, "card label");

  // Badge: pale green with dark green text, as "production ready" in the document. It keeps its colors in both modes.
  const badge = bounds(xml, "Conectado a la API");
  assertColor(shot.at(badge.left + 6, badge.top + 3), rgb(colors["status-production-bg"]), 1, "badge fill");
  assertColor(closestInside(shot, badge, rgb(colors["status-production-fg"])), rgb(colors["status-production-fg"]), 6, "badge text");
}

// The entry screen: the orange hero with the brand and the headline, and the sheet with the form: gray pill fields
// and the orange pill button. It is the brand moment, so it looks the same in dark mode (design-system.md section 7).
async function checkSignIn() {
  const xml = await waitForText("Iniciar sesión");
  const shot = screenshot();
  const orange = rgb(colors.primary);
  assertColor(shot.at(4, 4), orange, 1, "status bar strip");
  assertColor(shot.at(4, bounds(xml, "StockFlow").centerY), orange, 1, "hero");
  const title = bounds(xml, "Iniciar sesión");
  assertColor(shot.at(title.left - 4, title.centerY), rgb(colors.background), 1, "sheet");
  assertColor(closestInside(shot, title, rgb(colors["on-surface"])), rgb(colors["on-surface"]), 3, "sheet title");
  // The username field: its label sits inside the gray pill while the field is empty.
  const field = bounds(xml, "Usuario");
  assertColor(shot.at(field.right + 12, field.centerY), rgb(colors.surface), 1, "field");
  // The primary button: the design's orange with white text.
  const button = bounds(xml, "Entrar");
  assertColor(shot.at(button.left - 12, button.centerY), rgb(colors.primary), 1, "sign-in button");
}

// An emulator is switched to each mode. A phone is checked in the mode it is in, and its setting is not touched.
const nightBefore = /Night mode: (\w+)/.exec(adb("shell", "cmd", "uimode", "night"))[1];
if (isEmulator) after(() => adb("shell", "cmd", "uimode", "night", nightBefore));

for (const mode of isEmulator ? ["light", "dark"] : [darkMode() ? "dark" : "light"]) {
  test(`e2e: in ${mode} mode the home is the orange hero over the design's workspace`, async () => {
    if (isEmulator) adb("shell", "cmd", "uimode", "night", mode === "dark" ? "yes" : "no");
    await checkHome(mode);
  });
}
