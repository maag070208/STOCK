// The API, called from the end-to-end tests to set up their data, as the web and the app would (testing.md rule 4).
// The tests sign in as `e2e-admin`, which the API keeps only with the profile `e2e` (see .env.example).
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const base = process.env.API_URL ?? "http://localhost:8080/api/v1";

// A setting from the environment, or else from the repository's .env.
function setting(name) {
  if (process.env[name]) return process.env[name];
  const line = readFileSync(join(root, ".env"), "utf8").split("\n").find((entry) => entry.startsWith(`${name}=`));
  if (!line) throw new Error(`${name} is not set: add it to .env (see .env.example)`);
  return line.slice(name.length + 1).trim();
}

export async function call(method, path, { token, body, channel = "web" } = {}) {
  const headers = { "X-Client-Channel": channel };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const request = () => fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  // The API closes a connection that sat idle while a test drove the device. The next request opens a new one.
  const response = await request().catch(request);
  const text = await response.text();
  return { status: response.status, json: text ? JSON.parse(text) : null };
}

let admin;
export async function adminToken() {
  if (admin) return admin;
  const login = await call("POST", "/auth/login", { body: { username: "e2e-admin", password: setting("E2E_ADMIN_PASSWORD") } });
  if (login.status !== 200) throw new Error(`e2e-admin cannot sign in (${login.status}): start the API with the profile e2e`);
  admin = login.json.token;
  return admin;
}

// Letters, digits and hyphens only, so adb can type it.
const password = () => "Clave-" + randomUUID().replace(/-/g, "").slice(0, 10);

// A new person with these roles. With `pendingChange` they keep the temporary password, as a new account does.
// Otherwise they already chose a password of their own, on the given channel.
export async function newUser(roles, { pendingChange = false, channel = "app" } = {}) {
  const username = "e2e" + randomUUID().replace(/-/g, "").slice(0, 10);
  const created = await call("POST", "/users", { token: await adminToken(), body: { username, name: "Persona de Prueba", roles } });
  if (created.status !== 201) throw new Error(`user creation failed: ${JSON.stringify(created.json)}`);
  const temporary = created.json.temporaryPassword;
  if (pendingChange) return { username, password: temporary, newPassword: password() };
  const login = await call("POST", "/auth/login", { body: { username, password: temporary }, channel });
  const chosen = password();
  const changed = await call("POST", "/auth/change-password", { token: login.json.token, body: { currentPassword: temporary, newPassword: chosen }, channel });
  if (changed.status !== 200) throw new Error(`password change failed: ${JSON.stringify(changed.json)}`);
  return { username, password: chosen };
}

// A new active product, as an administrator creates it. Its code, which is also its barcode, has only letters and
// digits, so adb can type it and Code 128 can print it.
export async function newProduct(name) {
  const code = "E2E" + randomUUID().replace(/-/g, "").slice(0, 9).toUpperCase();
  const created = await call("POST", "/products", { token: await adminToken(), body: { code, name, unit: "PIECE" } });
  if (created.status !== 201) throw new Error(`product creation failed: ${JSON.stringify(created.json)}`);
  return { id: created.json.id, code, name };
}
