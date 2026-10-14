// F3-13 · e2e: finding a material from the app. A typed code shows its product; a name with three matches lists
// three and nothing is chosen until the person picks one; and, on the emulator, the camera reads a product's barcode
// and shows the product. A scan never creates anything (barcode-scanning.md section 3).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { adb, enter, fill, isEmulator, launch, signIn, tap, waitForText, waitUntil } from "./lib/android.mjs";
import { newProduct, newUser } from "./lib/api.mjs";
import { code128Png } from "./lib/barcode.mjs";

const app = "com.axzydev.stock";

async function openLookup(roles = ["WAREHOUSE_OPERATOR"], { camera = false } = {}) {
  const user = await newUser(roles);
  await launch({ fresh: true });
  // The camera permission is the app's own, given on the emulator only: a phone keeps the choices of its owner.
  if (camera) adb("shell", "pm", "grant", app, "android.permission.CAMERA");
  await signIn(user.username, user.password);
  const home = await waitUntil((xml) => xml.includes('resource-id="open-lookup"'), 30000);
  assert.ok(home, "the home offers the lookup to whoever reads products");
  tap(home, "open-lookup");
  await waitForText("Código, código de barras o nombre");
}

test("e2e: a typed code shows its product, and a name with three matches lists them until one is picked", async () => {
  const product = await newProduct("Codo galvanizado de prueba");
  const word = "Abrazadera" + randomUUID().replace(/-/g, "").slice(0, 6);
  const named = [];
  for (let i = 1; i <= 3; i++) named.push(await newProduct(`${word} ${i}`));
  await openLookup();

  await fill("lookup-value", product.code);
  enter();
  const found = await waitForText("Codo galvanizado de prueba");
  assert.match(found, /resource-id="lookup-product"/);
  assert.match(found, new RegExp(`text="${product.code}"`));

  // A new value replaces the old one: the field is emptied with the keyboard, then the name is typed.
  tap(found, "lookup-value");
  adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", ...Array(product.code.length).fill("KEYCODE_DEL"));
  adb("shell", "input", "text", word);
  enter();
  const list = await waitForText("3 materiales coinciden. Elige uno.");
  assert.doesNotMatch(list, /resource-id="lookup-product"/);
  tap(list, `candidate-${named[1].code}`);
  const picked = await waitUntil((xml) => xml.includes('resource-id="lookup-product"'), 10000);
  assert.ok(picked, "the picked product is shown");
  assert.match(picked, new RegExp(`text="${word} 2"`));
});

test("e2e: someone who cannot read products is not offered the lookup", async () => {
  const user = await newUser(["APP_USER"]);
  await launch({ fresh: true });
  await signIn(user.username, user.password);
  const home = await waitForText("Conectado a la API");
  assert.doesNotMatch(home, /resource-id="open-lookup"/);
});

// The emulator's camera shows a 3D scene with two posters in another room. The test puts the product's barcode on
// them and walks the camera there with the emulator's own macro. It needs the emulator's virtual scene camera
// (hw.camera.back=virtualscene, see mobile-app.md section 9); a phone's camera cannot be pointed by a test.
const sdk = process.env.ANDROID_HOME ?? join(homedir(), "Library/Android/sdk");
const macro = (name) => join(sdk, "emulator/resources/macros", name);
function virtualSceneCamera() {
  if (!isEmulator) return false;
  try {
    const avd = adb("emu", "avd", "path").split("\n")[0].trim();
    return /^hw\.camera\.back=virtualscene$/m.test(readFileSync(join(avd, "config.ini"), "utf8"));
  } catch {
    return false;
  }
}
const virtualScene = virtualSceneCamera();

test("e2e: the camera reads a product's barcode and shows the product", { skip: !virtualScene && "needs the emulator's virtual scene camera" }, async () => {
  const product = await newProduct("Tubo de prueba escaneado");
  const poster = join(tmpdir(), "stockflow-scan-poster.png");
  writeFileSync(poster, code128Png(product.code));
  adb("emu", "virtualscene-image", "wall", poster);
  adb("emu", "virtualscene-image", "table", poster);
  try {
    await openLookup(["WAREHOUSE_OPERATOR"], { camera: true });
    adb("emu", "automation", "play", macro("Walk_to_image_room"));
    const shown = await waitUntil((xml) => xml.includes('resource-id="lookup-product"'), 60000);
    assert.ok(shown, "the scanned product is shown");
    assert.match(shown, /text="Tubo de prueba escaneado"/);
    assert.match(shown, new RegExp(`text="${product.code}"`));
  } finally {
    // The scene goes back to how the emulator starts.
    adb("emu", "automation", "play", macro("Reset_position"));
    adb("emu", "virtualscene-image", "wall");
    adb("emu", "virtualscene-image", "table");
  }
});
