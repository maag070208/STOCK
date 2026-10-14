import type { Scope } from "@entities/user";

export interface PermissionDefinition {
	key: string;
	module: string;
	nameKey: string;
	scopes: Scope[];
	sensitive: boolean;
	active: boolean;
	sortOrder: number;
}

export interface Role {
	key: string;
	name: string | null;
	system: boolean;
	active: boolean;
	sortOrder: number;
	users: number;
	[key: string]: unknown;
}

export interface MatrixCell {
	roleKey: string;
	permissionKey: string;
	scope: Scope;
}

export interface PolicyCondition {
	field: string;
	operator: string;
	value: unknown;
}

export interface Policy {
	key: string;
	priority: number;
	effect: "ALLOW" | "DENY";
	action: string;
	active: boolean;
	conditions: PolicyCondition[];
	roles: string[];
	[key: string]: unknown;
}

export interface PolicyAction {
	key: string;
	fields: string[];
}
