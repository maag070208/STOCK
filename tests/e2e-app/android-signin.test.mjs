// F1-20 · e2e: sign-in on the app, the forced password change, the saved session and the sign-out.
import { test } from "node:test";
import assert from "node:assert/strict";
import { launch, restart, signIn, tap, fillForm, waitUntil } from "./lib/android.mjs";
import { newUser } from "./lib/api.mjs";

const home = (xml) => xml.includes('text="Conectado a la API"');

test("e2e: a new app user signs in with the temporary password, sets their own, and reaches the home", async () => {
  const user = await newUser(["APP_USER"], { pendingChange: true });
  await launch({ fresh: true });
  await signIn(user.username, user.password);
  assert.ok(await waitUntil((xml) => xml.includes('text="Cambia tu contraseña"'), 20000), "a temporary password leads to the password change");

  await fillForm("current-password", user.password, user.newPassword);
  assert.ok(await waitUntil(home, 20000), "after the change the home opens");
});

test("e2e: a user without app access sees the API's refusal in Spanish", async () => {
  const office = await newUser(["OFFICE"], { channel: "web" });
  await launch({ fresh: true });
  await signIn(office.username, office.password);
  const refused = await waitUntil((xml) => xml.includes("No tienes permiso para realizar esta acción."), 20000);
  assert.ok(refused, "the refusal is shown on the sign-in screen");
  assert.match(refused, /text="Iniciar sesión"/);
});

test("e2e: a wrong password is refused with the API's message", async () => {
  const user = await newUser(["APP_USER"]);
  await launch({ fresh: true });
  await signIn(user.username, "Clave-incorrecta1");
  assert.ok(await waitUntil((xml) => xml.includes("El usuario o la contraseña no son correctos."), 20000));
});

test("e2e: the session survives a restart, and signing out ends it", async () => {
  const user = await newUser(["WAREHOUSE_OPERATOR"]);
  await launch({ fresh: true });
  await signIn(user.username, user.password);
  assert.ok(await waitUntil(home, 20000), "signed in");

  restart();
  const restored = await waitUntil(home, 20000);
  assert.ok(restored, "the saved session opens the home without signing in again");

  tap(restored, "sign-out");
  assert.ok(await waitUntil((xml) => xml.includes('text="Iniciar sesión"'), 10000), "signing out shows the sign-in screen");
  restart();
  assert.ok(await waitUntil((xml) => xml.includes('text="Iniciar sesión"') && !xml.includes("Intro de StockFlow"), 20000), "after signing out no session is restored");
});
