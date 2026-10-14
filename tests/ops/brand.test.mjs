// F0-15 · unit (ops): the Android launcher and the iOS app icon set are in place and complete.
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const densities = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"];

test("android launcher has the icon in every density", () => {
  for (const d of densities) {
    for (const name of ["ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"]) {
      const file = join(root, "app/androidApp/src/main/res", `mipmap-${d}`, name);
      assert.ok(existsSync(file), `missing ${file}`);
    }
  }
  assert.ok(existsSync(join(root, "app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")));
});

test("ios app icon set lists the light, dark and tinted images, and every file exists", () => {
  const setDir = join(root, "app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset");
  const contents = JSON.parse(readFileSync(join(setDir, "Contents.json"), "utf8"));
  const named = contents.images.filter((image) => image.filename);
  assert.ok(named.length >= 3, "light, dark and tinted images must be present");
  for (const image of named) {
    assert.ok(existsSync(join(setDir, image.filename)), `missing ${image.filename}`);
  }
});

test("the android manifest points the launcher to the mipmap icon", () => {
  const manifest = readFileSync(join(root, "app/androidApp/src/main/AndroidManifest.xml"), "utf8");
  assert.match(manifest, /android:icon="@mipmap\/ic_launcher"/);
});
