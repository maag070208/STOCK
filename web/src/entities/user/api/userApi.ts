import { del, get, patch, post, put } from "@shared/api/client";
import { tableRequest, type TableParams } from "@shared/api/table";
import type { CreateUserInput, PermissionExceptionView, Scope, User } from "../model/types";

export const userApi = {
	query: (params: TableParams) => tableRequest<User>("/users/query", params),
	get: (id: string) => get<User>(`/users/${id}`),
	create: (input: CreateUserInput) => post<{ user: User; temporaryPassword: string }>("/users", input),
	update: (id: string, changes: Partial<Pick<User, "name" | "email" | "language">>) => patch<User>(`/users/${id}`, changes),
	deactivate: (id: string) => del<User>(`/users/${id}`),
	reactivate: (id: string) => post<User>(`/users/${id}/reactivate`),
	resetPassword: (id: string) => post<{ temporaryPassword: string }>(`/users/${id}/reset-password`),
	unlock: (id: string) => post<User>(`/users/${id}/unlock`),
	setRoles: (id: string, roles: string[]) => put<User>(`/users/${id}/roles`, { roles }),
	exceptions: (id: string) => get<PermissionExceptionView[]>(`/users/${id}/permissions`),
	setException: (id: string, permission: string, body: { scope: Scope; reason: string; expiresAt?: string | null }) =>
		put<PermissionExceptionView>(`/users/${id}/permissions/${permission}`, body),
	removeException: (id: string, permission: string) => del<void>(`/users/${id}/permissions/${permission}`),
};
