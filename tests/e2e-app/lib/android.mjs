// Helpers for the Android end-to-end tests: adb, the accessibility dump and the screen's pixels.
import { execFileSync } from "node:child_process";
import { readFileSync, unlinkSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";
import { inflateSync } from "node:zlib";

export const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const apk = join(root, "app/androidApp/build/outputs/apk/debug/androidApp-debug.apk");

export const adb = (...args) => execFileSync("adb", args, { encoding: "utf8" });
export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// The device the tests drive. With several connected, ANDROID_SERIAL chooses one: adb reads the same variable.
const connected = adb("devices").split("\n").filter((line) => line.endsWith("\tdevice")).map((line) => line.split("\t")[0]);
if (connected.length === 0) throw new Error("No Android device or emulator is connected.");
if (connected.length > 1 && !process.env.ANDROID_SERIAL) {
  throw new Error(`Several Android devices are connected (${connected.join(", ")}). Set ANDROID_SERIAL to choose one.`);
}
export const serial = process.env.ANDROID_SERIAL ?? connected[0];

// A test changes system settings (dark mode, animation scale) only on an emulator. A person's phone keeps its settings.
export const isEmulator = serial.startsWith("emulator-");

const app = "com.axzydev.stock";

// Installs the debug build and opens it. The app reaches the local API through the device's port forward.
// `fresh` clears the app's data first, so no saved session opens it.
export async function launch({ fresh = false } = {}) {
  adb("reverse", "tcp:8080", "tcp:8080");
  adb("install", "-r", apk);
  adb("shell", "am", "force-stop", app);
  if (fresh) adb("shell", "pm", "clear", app);
  // The accessibility tree can still show the stopped app for a moment. Wait until it is gone, so a later read is the new launch.
  for (let i = 0; i < 20 && dump().includes('package="com.axzydev.stock"'); i++) await sleep(250);
  adb("shell", "am", "start", "-n", `${app}/.MainActivity`);
}

// Opens the app again without installing or clearing anything: a saved session is restored.
export function restart() {
  adb("shell", "am", "force-stop", app);
  adb("shell", "am", "start", "-n", `${app}/.MainActivity`);
}

// Taps the element with this test tag. The app exposes its test tags as resource ids.
export function tap(xml, tag) {
  const match = new RegExp(`resource-id="${tag}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`).exec(xml);
  if (!match) throw new Error(`no element tagged "${tag}" on screen`);
  const [left, top, right, bottom] = match.slice(1).map(Number);
  adb("shell", "input", "tap", String(Math.round((left + right) / 2)), String(Math.round((top + bottom) / 2)));
}

// Types into the field that has the cursor. The values the tests type have no spaces or shell characters.
export function type(text) {
  adb("shell", "input", "text", text);
}

// Fills a field: taps it, then types.
export async function fill(tag, text) {
  const xml = await waitUntil((screen) => screen.includes(`resource-id="${tag}"`), 15000);
  if (!xml) throw new Error(`no field tagged "${tag}" appeared`);
  tap(xml, tag);
  await sleep(300);
  type(text);
}

// The keyboard's action key: Next moves to the following field, Done sends the form.
export function enter() {
  adb("shell", "input", "keyevent", "66");
}

// Fills a form as a person would on a phone: taps the first field, types, and moves on with the keyboard's Next key.
// The keyboard covers the fields below, so they are reached with the keyboard, not with a tap. The last value is sent with Done.
export async function fillForm(firstTag, ...values) {
  await fill(firstTag, values[0]);
  for (const value of values.slice(1)) {
    enter();
    await sleep(300);
    type(value);
  }
  enter();
}

export async function signIn(username, password) {
  await fillForm("username", username, password);
}

// The screen as the accessibility tree sees it. Empty when the tree cannot be read at this moment,
// which happens while an app is starting: the file on the device would then be an older screen.
export function dump() {
  const result = adb("shell", "uiautomator", "dump", "/sdcard/stockflow-screen.xml");
  if (!result.includes("dumped to")) return "";
  const local = join(tmpdir(), "stockflow-screen.xml");
  adb("pull", "/sdcard/stockflow-screen.xml", local);
  const xml = readFileSync(local, "utf8");
  unlinkSync(local);
  return xml;
}

// A window of the system itself over the app, such as a password manager offering to save the sign-in
// (Samsung Pass, Google). Its nodes all belong to the package "android".
const systemDialog = (xml) => {
  const packages = new Set([...xml.matchAll(/package="([^"]+)"/g)].map((match) => match[1]));
  return packages.size === 1 && packages.has("android");
};

// Reads the screen until the condition holds. Gives the screen that passed, or null after the time limit.
// A system dialog over the app is dismissed with Back, which declines it without changing any setting.
export async function waitUntil(condition, limitMs) {
  const deadline = Date.now() + limitMs;
  while (Date.now() < deadline) {
    const xml = dump();
    if (condition(xml)) return xml;
    if (xml && systemDialog(xml)) adb("shell", "input", "keyevent", "4");
    await sleep(150);
  }
  return null;
}

export async function waitForText(text, limitMs = 20000) {
  const xml = await waitUntil((screen) => screen.includes(`text="${text}"`), limitMs);
  if (!xml) throw new Error(`"${text}" did not appear on screen`);
  return xml;
}

// True when the device is showing dark mode now, whatever setting or schedule got it there.
export function darkMode() {
  const setting = /Night mode: (\w+)/.exec(adb("shell", "cmd", "uimode", "night"))[1];
  if (setting === "yes" || setting === "no") return setting === "yes";
  return adb("shell", "dumpsys", "uimode").includes("mComputedNightMode=true");
}

// Where a text sits on screen, in pixels.
export function bounds(xml, text) {
  const match = new RegExp(`text="${text}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`).exec(xml);
  if (!match) throw new Error(`no node with text "${text}"`);
  const [left, top, right, bottom] = match.slice(1).map(Number);
  return { left, top, right, bottom, centerY: Math.round((top + bottom) / 2) };
}

// The screen's pixels. `at(x, y)` gives [red, green, blue].
export function screenshot() {
  const png = execFileSync("adb", ["exec-out", "screencap", "-p"], { maxBuffer: 256 * 1024 * 1024 });
  let width = 0;
  let height = 0;
  let channels = 0;
  const data = [];
  for (let offset = 8; offset < png.length; ) {
    const length = png.readUInt32BE(offset);
    const type = png.toString("ascii", offset + 4, offset + 8);
    const body = png.subarray(offset + 8, offset + 8 + length);
    if (type === "IHDR") {
      width = body.readUInt32BE(0);
      height = body.readUInt32BE(4);
      if (body[8] !== 8 || (body[9] !== 6 && body[9] !== 2)) throw new Error("unexpected screenshot format");
      channels = body[9] === 6 ? 4 : 3;
    }
    if (type === "IDAT") data.push(body);
    offset += 12 + length;
  }
  // Each row starts with a filter byte; the filters rebuild a byte from its neighbors.
  const raw = inflateSync(Buffer.concat(data));
  const stride = width * channels;
  const pixels = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const left = x >= channels ? pixels[y * stride + x - channels] : 0;
      const up = y > 0 ? pixels[(y - 1) * stride + x] : 0;
      const upLeft = x >= channels && y > 0 ? pixels[(y - 1) * stride + x - channels] : 0;
      let value = raw[y * (stride + 1) + 1 + x];
      if (filter === 1) value += left;
      else if (filter === 2) value += up;
      else if (filter === 3) value += (left + up) >> 1;
      else if (filter === 4) {
        const p = left + up - upLeft;
        const [a, b, c] = [Math.abs(p - left), Math.abs(p - up), Math.abs(p - upLeft)];
        value += a <= b && a <= c ? left : b <= c ? up : upLeft;
      }
      pixels[y * stride + x] = value & 255;
    }
  }
  const at = (x, y) => [0, 1, 2].map((channel) => pixels[y * stride + x * channels + channel]);
  return { width, height, at };
}
