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

export async function call(method, path, { token, body, channel = "web", headers = {} } = {}) {
  const all = { "X-Client-Channel": channel, ...headers };
  if (token) all.Authorization = `Bearer ${token}`;
  if (body !== undefined) all["Content-Type"] = "application/json";
  const request = () => fetch(base + path, { method, headers: all, body: body === undefined ? undefined : JSON.stringify(body) });
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
  const id = created.json.user.id;
  const temporary = created.json.temporaryPassword;
  if (pendingChange) return { id, username, password: temporary, newPassword: password() };
  const login = await call("POST", "/auth/login", { body: { username, password: temporary }, channel });
  const chosen = password();
  const changed = await call("POST", "/auth/change-password", { token: login.json.token, body: { currentPassword: temporary, newPassword: chosen }, channel });
  if (changed.status !== 200) throw new Error(`password change failed: ${JSON.stringify(changed.json)}`);
  return { id, username, password: chosen, token: changed.json.token };
}

// A new active product, as an administrator creates it. Its code, which is also its barcode, has only letters and
// digits, so adb can type it and Code 128 can print it.
export async function newProduct(name) {
  const code = "E2E" + randomUUID().replace(/-/g, "").slice(0, 9).toUpperCase();
  const created = await call("POST", "/products", { token: await adminToken(), body: { code, name, unit: "PIECE" } });
  if (created.status !== 201) throw new Error(`product creation failed: ${JSON.stringify(created.json)}`);
  return { id: created.json.id, code, name };
}

// A warehouse with a rack of its own, and the people who work in it.
export async function newWarehouse() {
  const code = "W-" + randomUUID().slice(0, 8).toUpperCase();
  const created = await call("POST", "/warehouses", { token: await adminToken(), body: { code, name: "Almacén de prueba" } });
  if (created.status !== 201) throw new Error(`warehouse creation failed: ${JSON.stringify(created.json)}`);
  return { id: created.json.id, code };
}

export async function newRack(warehouseId, { aisle = "K", rack = "05", levels = 2 } = {}) {
  const created = await call("POST", "/locations/racks", { token: await adminToken(), body: { warehouseId, aisle, rack, levels } });
  if (created.status !== 201) throw new Error(`rack creation failed: ${JSON.stringify(created.json)}`);
  return created.json.locations.map((location) => ({ id: location.id, code: location.code }));
}

// The staff template of a warehouse: the operators an order can be assigned to (order-fulfillment.md section 7a).
export async function staff(warehouseId, userIds) {
  const updated = await call("PUT", `/warehouses/${warehouseId}/staff`, { token: await adminToken(), body: { userIds } });
  if (updated.status !== 200) throw new Error(`staff template failed: ${JSON.stringify(updated.json)}`);
}

// A permission an older database may not have seeded yet, for the warehouse it is granted in.
export async function grant(userId, permission, scope, reason = "Prueba de extremo a extremo") {
  const granted = await call("PUT", `/users/${userId}/permissions/${permission}`, { token: await adminToken(), body: { scope, reason } });
  if (granted.status !== 200) throw new Error(`permission ${permission} failed: ${JSON.stringify(granted.json)}`);
}

// The partner rules of the API (docs/modules/partners.md): a payment term, an address, and a valid tax id.
async function paymentTerms() {
  const labels = { es: { label: "Plazo de prueba" }, en: { label: "Test term" } };
  const created = await call("POST", "/catalogs/PAYMENT_TERMS/items", { token: await adminToken(), body: { code: "E2E_TERMS", labels } });
  if (created.status !== 201 && created.json?.code !== "DUPLICATE_VALUE") throw new Error(`payment term failed: ${JSON.stringify(created.json)}`);
  return "E2E_TERMS";
}

async function partner(kind, name) {
  const body = {
    code: uniqueCode(kind === "customer" ? "CL" : "PR"), name, taxId: "ADN010203AB1", paymentTerms: await paymentTerms(),
    address: { street: "Av. Juárez", exteriorNumber: "120", neighborhood: "Centro", city: "Guadalajara", state: "MX_JAL", postalCode: "44100", country: "MX" },
  };
  const created = await call("POST", `/${kind}s`, { token: await adminToken(), body });
  if (created.status !== 201) throw new Error(`${kind} creation failed: ${JSON.stringify(created.json)}`);
  return { id: created.json.id, code: created.json.code, name: created.json.name };
}

export const uniqueCode = (prefix) => `${prefix}-${randomUUID().replace(/-/g, "").slice(0, 10).toUpperCase()}`;
export const newCustomer = (name = "Cliente de prueba") => partner("customer", name);
export const newSupplier = (name = "Proveedor de prueba") => partner("supplier", name);

// Stock in one real location of a warehouse, through the material entry flow the receiving screens use: an invoice of
// the operator's warehouse, its assignment, the count and the placement that writes the RECEIPT movement. The opening
// inventory would leave it in TEMPORARY, and a pick is taken from the shelves.
export async function stockIn({ warehouseId, locationId, operator, product, quantity }) {
  const token = await adminToken();
  const supplier = await newSupplier();
  const invoice = await call("POST", "/invoices", {
    token,
    headers: { "Idempotency-Key": randomUUID() },
    body: { number: "F-" + randomUUID().slice(0, 8).toUpperCase(), supplierId: supplier.id, invoiceDate: new Date().toISOString().slice(0, 10), warehouseId, lines: [{ productId: product.id, quantity }] },
  });
  if (invoice.status !== 201) throw new Error(`invoice creation failed: ${JSON.stringify(invoice.json)}`);
  const lineId = invoice.json.lines[0].id;
  const assigned = await call("PUT", `/invoices/${invoice.json.id}/lines/${lineId}/assignments`, {
    token,
    body: { assignments: [{ operatorId: operator.id, quantity }] },
  });
  if (assigned.status !== 200) throw new Error(`assignment failed: ${JSON.stringify(assigned.json)}`);
  const assignmentId = assigned.json.assignments[0].id;
  const body = { countedQuantity: String(quantity) };
  const counted = await call("POST", `/receipts/assignments/${assignmentId}/count`, { token: operator.token, body, channel: "app" });
  if (counted.status !== 200) throw new Error(`count failed: ${JSON.stringify(counted.json)}`);
  const placed = await call("POST", `/receipts/assignments/${assignmentId}/placements`, {
    token: operator.token,
    body: { placements: [{ locationId, quantity: String(quantity) }] },
    channel: "app",
  });
  if (placed.status !== 200) throw new Error(`placement failed: ${JSON.stringify(placed.json)}`);
}

// An order for a material, assigned to the operator who fulfills it. Creation takes an Idempotency-Key.
export async function newOrder({ warehouseId, customerId, product, quantity, operatorId }) {
  const number = "ORD-" + randomUUID().slice(0, 8).toUpperCase();
  const created = await call("POST", "/orders", {
    token: await adminToken(),
    headers: { "Idempotency-Key": randomUUID() },
    body: { number, warehouseId, customerId, dueDate: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10), lines: [{ productId: product.id, quantity }] },
  });
  if (created.status !== 201) throw new Error(`order creation failed: ${JSON.stringify(created.json)}`);
  const assigned = await call("PUT", `/orders/${created.json.id}/assignment`, { token: await adminToken(), body: { operatorId } });
  if (assigned.status !== 200) throw new Error(`order assignment failed: ${JSON.stringify(assigned.json)}`);
  return created.json;
}

export async function order(id) {
  const read = await call("GET", `/orders/${id}`, { token: await adminToken() });
  if (read.status !== 200) throw new Error(`order read failed: ${JSON.stringify(read.json)}`);
  return read.json;
}

export async function balances(warehouseId, productId) {
  const read = await call("POST", "/stock/balances/query", {
    token: await adminToken(),
    body: { filters: { warehouseId, productId }, limit: 200 },
  });
  if (read.status !== 200) throw new Error(`balances failed: ${JSON.stringify(read.json)}`);
  return read.json.data;
}
