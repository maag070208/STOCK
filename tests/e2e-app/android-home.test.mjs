// F0-13 and F1-20 · e2e: after signing in, the home screen greets the person and shows the API status.
import { test } from "node:test";
import assert from "node:assert/strict";
import { launch, signIn, waitUntil } from "./lib/android.mjs";
import { newUser } from "./lib/api.mjs";

test("e2e: the home screen shows Conectado a la API when the API is up", async () => {
  const user = await newUser(["APP_USER"]);
  await launch({ fresh: true });
  await signIn(user.username, user.password);
  const home = await waitUntil((xml) => xml.includes('text="Conectado a la API"'), 20000);
  assert.ok(home, "home must show the API as connected");
  assert.match(home, /text="Hola, Persona de Prueba"/);
});
