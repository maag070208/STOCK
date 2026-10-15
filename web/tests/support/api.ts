// The API, called by the end-to-end tests to set up their data through its endpoints (testing.md rule 4).
// The tests sign in as `e2e-admin`, which the API keeps only with the profile `e2e` (see .env.example).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";
import type { Page } from "@playwright/test";

const base = process.env.API_URL ?? "http://localhost:8080/api/v1";

function setting(name: string): string {
	if (process.env[name]) return process.env[name]!;
	const line = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..", ".env"), "utf8").split("\n").find((entry) => entry.startsWith(`${name}=`));
	if (!line) throw new Error(`${name} is not set: add it to .env (see .env.example)`);
	return line.slice(name.length + 1).trim();
}

export async function call(method: string, path: string, options: { token?: string; body?: unknown; channel?: string; headers?: Record<string, string> } = {}) {
	// `headers` adds request headers, such as the Idempotency-Key a creation takes (api.md section 2).
	const headers: Record<string, string> = { "X-Client-Channel": options.channel ?? "web", ...options.headers };
	if (options.token) headers.Authorization = `Bearer ${options.token}`;
	if (options.body !== undefined) headers["Content-Type"] = "application/json";
	const request = () => fetch(base + path, { method, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) });
	const response = await request().catch(request);
	const text = await response.text();
	const json = text ? JSON.parse(text) : null;
	if (response.status < 400) track(method, path, options.body, json);
	return { status: response.status, json };
}

// Sends a file as a form, the way the web uploads it: the file in `file`, the other fields as text.
export async function upload(path: string, token: string, file: Buffer, fields: Record<string, string> = {}) {
	const form = new FormData();
	form.append("file", new Blob([file]), "inventario.xlsx");
	for (const [name, value] of Object.entries(fields)) form.append(name, value);
	const response = await fetch(base + path, { method: "POST", headers: { "X-Client-Channel": "web", Authorization: `Bearer ${token}` }, body: form });
	const text = await response.text();
	return { status: response.status, json: text ? JSON.parse(text) : null };
}

// What a test creates, so it can take it away when the test ends. The API is the only way in and the only way out
// (testing.md rule 4): a resource that has no endpoint to remove it is left for the reset of the whole database
// (tests/support/teardown.ts).
interface Created {
	label: string;
	remove: string;
}

const created: Created[] = [];

// How each thing a test creates is taken away: the path it was created at, where its key comes from, and the path that
// removes it. The order of the list does not matter: the removals run backwards, the last created first.
const removable: { path: RegExp; key: (json: any, body: any) => string | null; remove: (key: string) => string }[] = [
	{ path: /^\/users$/, key: (json) => json?.user?.id ?? null, remove: (id) => `/users/${id}` },
	{ path: /^\/suppliers$/, key: (json) => json?.id ?? null, remove: (id) => `/suppliers/${id}` },
	{ path: /^\/customers$/, key: (json) => json?.id ?? null, remove: (id) => `/customers/${id}` },
	{ path: /^\/suppliers\/[^/]+\/contacts$/, key: (json) => json?.id ?? null, remove: (id) => `/suppliers/contacts/${id}` },
	{ path: /^\/customers\/[^/]+\/contacts$/, key: (json) => json?.id ?? null, remove: (id) => `/customers/contacts/${id}` },
	{ path: /^\/products$/, key: (json) => json?.id ?? null, remove: (id) => `/products/${id}` },
	{ path: /^\/warehouses$/, key: (json) => json?.id ?? null, remove: (id) => `/warehouses/${id}` },
	{ path: /^\/roles$/, key: (_json, body) => body?.key ?? null, remove: (key) => `/roles/${key}` },
	{ path: /^\/policies$/, key: (_json, body) => body?.key ?? null, remove: (key) => `/policies/${key}` },
];

function track(method: string, path: string, body: unknown, json: any) {
	if (method !== "POST") return;
	const rule = removable.find((candidate) => candidate.path.test(path));
	if (!rule) return;
	const key = rule.key(json, body);
	if (key) created.push({ label: path, remove: rule.remove(key) });
}

// How much has been created so far. A test takes this before it starts, so it removes what it wrote and not what a
// `beforeAll` of its file wrote, which the whole file still needs.
export const createdMark = (): number => created.length;

// Empties what was created after that mark, in reverse order, and never fails the test: a resource another test already
// took away is not a reason to call a passing test a failure.
export async function drainCreated(since = 0, token?: string) {
	const auth = token ?? (await adminToken().catch(() => undefined));
	while (created.length > since) {
		const item = created.pop()!;
		if (!auth) continue;
		await call("DELETE", item.remove, { token: auth }).catch(() => undefined);
	}
}

// The test administrator of the profile e2e.
export const e2eAdmin = () => ({ username: "e2e-admin", password: setting("E2E_ADMIN_PASSWORD") });

let admin: string | undefined;
export async function adminToken(): Promise<string> {
	if (admin) return admin;
	const login = await call("POST", "/auth/login", { body: e2eAdmin() });
	if (login.status !== 200) throw new Error(`e2e-admin cannot sign in (${login.status}): start the API with the profile e2e`);
	admin = login.json.token as string;
	return admin;
}

export interface TestUser {
	id: string;
	username: string;
	name: string;
	password: string;
	newPassword: string;
}

export const uniqueName = (prefix = "w") => prefix + randomUUID().replace(/-/g, "").slice(0, 10);

// The business day of the API (America/Mexico_City). Invoice dates and order due dates are checked against it, so a
// test that used the UTC calendar would fail every evening: UTC has already rolled over and the business day has not.
// `en-CA` formats a date as YYYY-MM-DD, which is what the API reads.
export const businessDate = (daysAhead = 0): string =>
	new Intl.DateTimeFormat("en-CA", { timeZone: "America/Mexico_City", year: "numeric", month: "2-digit", day: "2-digit" })
		.format(new Date(Date.now() + daysAhead * 86_400_000));

// A payment term for suppliers and customers. The owner has not given the real ones, so the tests add their own,
// marked as test data, the same one the API's suite uses. It is created once; later calls find it.
export async function testPaymentTerms(): Promise<{ code: string; label: string }> {
	const labels = { es: { label: "Plazo de prueba" }, en: { label: "Test term" } };
	const created = await call("POST", "/catalogs/PAYMENT_TERMS/items", { token: await adminToken(), body: { code: "E2E_TERMS", labels } });
	if (created.status !== 201 && created.json?.code !== "DUPLICATE_VALUE") throw new Error(`payment term creation failed: ${JSON.stringify(created.json)}`);
	return { code: "E2E_TERMS", label: "Plazo de prueba" };
}

// A new person with these roles. With `pendingChange` they keep the temporary password, as a new account does.
export async function newUser(roles: string[], { pendingChange = false, channel = "web" } = {}): Promise<TestUser> {
	const username = uniqueName();
	const name = `Persona ${username.slice(-4)}`;
	const created = await call("POST", "/users", { token: await adminToken(), body: { username, name, roles } });
	if (created.status !== 201) throw new Error(`user creation failed: ${JSON.stringify(created.json)}`);
	const temporary = created.json.temporaryPassword as string;
	const user = { id: created.json.user.id as string, username, name, password: temporary, newPassword: "Clave-" + randomUUID().slice(0, 12) };
	if (pendingChange) return user;
	const login = await call("POST", "/auth/login", { body: { username, password: temporary }, channel });
	const changed = await call("POST", "/auth/change-password", { token: login.json.token, body: { currentPassword: temporary, newPassword: user.newPassword }, channel });
	if (changed.status !== 200) throw new Error(`password change failed: ${JSON.stringify(changed.json)}`);
	return { ...user, password: user.newPassword };
}

// Opens the web already signed in as this person: the session is the one the API gives, stored as the web stores it.
// The brand intro is marked as seen, so it does not cover the page.
export async function signInAs(page: Page, user: { username: string; password: string }) {
	const login = await call("POST", "/auth/login", { body: { username: user.username, password: user.password } });
	if (login.status !== 200) throw new Error(`sign-in failed: ${JSON.stringify(login.json)}`);
	const session = { token: login.json.token, refreshToken: login.json.refreshToken, user: login.json.user };
	await page.addInitScript((value) => {
		window.localStorage.setItem("stockflow.auth", value);
		window.sessionStorage.setItem("stockflow.intro-seen", "1");
	}, JSON.stringify(session));
}

export async function skipIntro(page: Page) {
	await page.addInitScript(() => window.sessionStorage.setItem("stockflow.intro-seen", "1"));
}
