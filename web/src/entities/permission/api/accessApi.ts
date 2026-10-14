import { del, get, patch, post, put } from "@shared/api/client";
import type { MatrixCell, PermissionDefinition, Policy, PolicyAction, Role } from "../model/types";

export const accessApi = {
	permissions: () => get<PermissionDefinition[]>("/permissions"),
	roles: () => get<Role[]>("/roles"),
	createRole: (key: string, name: string) => post<Role>("/roles", { key, name }),
	updateRole: (key: string, changes: { name?: string; active?: boolean; sortOrder?: number }) => patch<Role>(`/roles/${key}`, changes),
	deleteRole: (key: string) => del<void>(`/roles/${key}`),
	matrix: () => get<MatrixCell[]>("/roles/matrix"),
	changeMatrix: (changes: MatrixCell[]) => put<MatrixCell[]>("/roles/matrix", { changes }),
	policies: () => get<Policy[]>("/policies"),
	policyActions: () => get<PolicyAction[]>("/policies/actions"),
	createPolicy: (policy: Omit<Policy, "active"> & { active?: boolean }) => post<Policy>("/policies", policy),
	updatePolicy: (key: string, policy: Omit<Policy, "key">) => put<Policy>(`/policies/${key}`, policy),
	deletePolicy: (key: string) => del<void>(`/policies/${key}`),
};
