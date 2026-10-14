// F1-19 · unit: the matrix shows the scope of each cell from the API, and "what each role sees" comes from APP_SCREENS.
import { test, expect } from "@playwright/test";
import { matrixRows, parsePolicyValue, formatPolicyValue, screensOfRole } from "../../src/entities/permission/model/matrix";
import { APP_SCREENS } from "../../src/entities/permission/model/screens";
import type { PermissionDefinition, Role } from "../../src/entities/permission/model/types";

const permission = (key: string, sortOrder: number): PermissionDefinition => ({ key, module: key.split(".")[0], nameKey: `permissions.${key}`, scopes: ["ALL"], sensitive: false, active: true, sortOrder });
const role = (key: string): Role => ({ key, name: null, system: true, active: true, sortOrder: 0, users: 0 });

test("each cell shows the scope the API gave, and a missing cell is NONE", () => {
	const rows = matrixRows(
		[permission("catalogs.view", 2), permission("users.view", 1)],
		[role("ADMIN"), role("OFFICE")],
		[
			{ roleKey: "ADMIN", permissionKey: "users.view", scope: "ALL" },
			{ roleKey: "ADMIN", permissionKey: "catalogs.view", scope: "ALL" },
			{ roleKey: "OFFICE", permissionKey: "catalogs.view", scope: "ALL" },
		],
	);
	expect(rows.map((row) => [row.permission.key, row.scopes])).toEqual([
		["users.view", ["ALL", "NONE"]],
		["catalogs.view", ["ALL", "ALL"]],
	]);
});

test("a role sees the screens whose permission it holds, and home always", () => {
	const cells = [
		{ roleKey: "OFFICE", permissionKey: "catalogs.view", scope: "ALL" as const },
		{ roleKey: "AUDITOR", permissionKey: "audit.view", scope: "ALL" as const },
	];
	expect(screensOfRole(APP_SCREENS, cells, "OFFICE").map((s) => s.id)).toEqual(["home", "catalogs"]);
	expect(screensOfRole(APP_SCREENS, cells, "AUDITOR").map((s) => s.id)).toEqual(["home", "audit"]);
	expect(screensOfRole(APP_SCREENS, cells, "NOBODY").map((s) => s.id)).toEqual(["home"]);
});

test("a policy value is read as a person types it", () => {
	expect(parsePolicyValue("100")).toBe(100);
	expect(parsePolicyValue("true")).toBe(true);
	expect(parsePolicyValue("CREATED")).toBe("CREATED");
	expect(parsePolicyValue("ADMIN, OFFICE")).toEqual(["ADMIN", "OFFICE"]);
	expect(parsePolicyValue("1, 2")).toEqual([1, 2]);
	expect(formatPolicyValue(["ADMIN", "OFFICE"])).toBe("ADMIN, OFFICE");
});
