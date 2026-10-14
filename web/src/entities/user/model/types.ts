export type Scope = "NONE" | "OWN" | "AREA" | "ALL";

// Permission key → scope. Only scopes above NONE are present.
export type PermissionMap = Record<string, Scope>;

// The signed-in person, as GET /auth/me describes them.
export interface SessionUser {
	id: string;
	username: string;
	name: string;
	email: string | null;
	language: string;
	mustChangePassword: boolean;
	roles: string[];
	permissions: PermissionMap;
}

export interface Tokens {
	token: string;
	refreshToken: string;
}

export interface LoginResult extends Tokens {
	user: SessionUser;
}

export interface User {
	id: string;
	username: string;
	name: string;
	email: string | null;
	language: string;
	active: boolean;
	locked: boolean;
	mustChangePassword: boolean;
	roles: string[];
	warehouseIds: string[];
	lastLoginAt: string | null;
	createdAt: string;
	[key: string]: unknown;
}

export interface PermissionExceptionView {
	permission: string;
	scope: Scope;
	reason: string;
	expiresAt: string | null;
	grantedBy: string;
	createdAt: string;
}

export interface CreateUserInput {
	username: string;
	name: string;
	email?: string;
	language?: string;
	roles?: string[];
}
