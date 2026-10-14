import { get, patch, post } from "@shared/api/client";
import type { LoginResult, SessionUser, Tokens } from "../model/types";

export const authApi = {
	login: (username: string, password: string) => post<LoginResult>("/auth/login", { username, password }),
	me: () => get<{ user: SessionUser }>("/auth/me").then((r) => r.user),
	updateMe: (language: string) => patch<{ user: SessionUser }>("/auth/me", { language }).then((r) => r.user),
	changePassword: (currentPassword: string, newPassword: string) => post<Tokens>("/auth/change-password", { currentPassword, newPassword }),
	logout: (refreshToken: string | null) => post<void>("/auth/logout", refreshToken ? { refreshToken } : {}),
};
