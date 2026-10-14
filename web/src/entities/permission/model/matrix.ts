import type { Scope } from "@entities/user";
import type { AppScreen } from "./screens";
import type { MatrixCell, PermissionDefinition, Role } from "./types";

// The scope a role holds for a permission. A missing cell is NONE.
export function cellScope(cells: MatrixCell[], roleKey: string, permissionKey: string): Scope {
	return cells.find((cell) => cell.roleKey === roleKey && cell.permissionKey === permissionKey)?.scope ?? "NONE";
}

export interface MatrixRow {
	permission: PermissionDefinition;
	// The scope of each role, in the order of the roles given.
	scopes: Scope[];
}

// The matrix as the screen draws it: one row per permission, in catalog order, one column per role.
export function matrixRows(permissions: PermissionDefinition[], roles: Role[], cells: MatrixCell[]): MatrixRow[] {
	return [...permissions]
		.sort((a, b) => a.sortOrder - b.sortOrder)
		.map((permission) => ({ permission, scopes: roles.map((role) => cellScope(cells, role.key, permission.key)) }));
}

// What a role sees: the screens whose permissions the role holds through the matrix (web.md section 4).
// A screen that asks for no permission is seen by every role.
export function screensOfRole(screens: readonly AppScreen[], cells: MatrixCell[], roleKey: string): AppScreen[] {
	return screens.filter((screen) => !screen.anyOf || screen.anyOf.some((key) => cellScope(cells, roleKey, key) !== "NONE"));
}

// A policy value as a person types it: true or false, a number, a comma-separated list, or text.
export function parsePolicyValue(text: string): unknown {
	const value = text.trim();
	if (value.includes(",")) return value.split(",").map((part) => parsePolicyValue(part)).filter((part) => part !== "");
	if (value === "true" || value === "false") return value === "true";
	if (value !== "" && !Number.isNaN(Number(value))) return Number(value);
	return value;
}

export function formatPolicyValue(value: unknown): string {
	return Array.isArray(value) ? value.map(formatPolicyValue).join(", ") : String(value ?? "");
}
