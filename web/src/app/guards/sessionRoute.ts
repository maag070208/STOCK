import type { SessionUser } from "@entities/user";

// Where a session may be: without one, only the sign-in; with a temporary password, only the password change.
// Returns the path to send the person to, or null when they may stay.
export function sessionRedirect(session: { token: string | null; user: SessionUser | null }, path: string): string | null {
	if (!session.token) return path === "/login" ? null : "/login";
	if (session.user?.mustChangePassword) return path === "/change-password" ? null : "/change-password";
	if (path === "/login") return "/";
	return null;
}
