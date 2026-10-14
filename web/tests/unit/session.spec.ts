// F1-18 · unit: the router sends a person with a temporary password to the password change, and to nothing else;
// the menu shows only the screens the person's permissions allow.
import { test, expect } from "@playwright/test";
import { sessionRedirect } from "../../src/app/guards/sessionRoute";
import { APP_SCREENS, isScreenVisible } from "../../src/entities/permission/model/screens";
import type { SessionUser } from "../../src/entities/user/model/types";

const user = (mustChangePassword: boolean, permissions: Record<string, "ALL"> = {}): SessionUser => ({
	id: "u-1", username: "ana", name: "Ana", email: null, language: "es", mustChangePassword, roles: [], permissions,
});

test("without a session, only the sign-in screen", () => {
	const none = { token: null, user: null };
	expect(sessionRedirect(none, "/")).toBe("/login");
	expect(sessionRedirect(none, "/users")).toBe("/login");
	expect(sessionRedirect(none, "/change-password")).toBe("/login");
	expect(sessionRedirect(none, "/login")).toBeNull();
});

test("with a temporary password, the password change and nothing else", () => {
	const pending = { token: "t", user: user(true, { "users.view": "ALL" }) };
	for (const path of ["/", "/users", "/roles", "/login"]) expect(sessionRedirect(pending, path)).toBe("/change-password");
	expect(sessionRedirect(pending, "/change-password")).toBeNull();
});

test("with a session, the sign-in screen sends home and every other path stays", () => {
	const session = { token: "t", user: user(false) };
	expect(sessionRedirect(session, "/login")).toBe("/");
	expect(sessionRedirect(session, "/users")).toBeNull();
	expect(sessionRedirect(session, "/change-password")).toBeNull();
});

test("the menu shows only the screens the permissions allow", () => {
	const visible = (permissions: Record<string, "ALL">) => APP_SCREENS.filter((screen) => isScreenVisible(permissions, screen)).map((s) => s.id);
	expect(visible({})).toEqual(["home"]);
	expect(visible({ "catalogs.view": "ALL", "web.access": "ALL" })).toEqual(["home", "catalogs"]);
	expect(visible({ "users.view": "ALL", "roles.manage": "ALL", "catalogs.view": "ALL", "audit.view": "ALL" })).toEqual(["home", "users", "roles", "catalogs", "audit"]);
});
