import { useSelector } from "react-redux";
import type { PermissionMap, Scope, SessionUser } from "./types";

type WithAuth = { auth: { user: SessionUser | null } };

export const useSessionUser = () => useSelector((state: WithAuth) => state.auth.user);

export const usePermissions = (): PermissionMap => useSelector((state: WithAuth) => state.auth.user?.permissions ?? {});

// The scope the person holds for a permission. The API decides on every request; this only shapes what the screen shows.
export const usePermission = (key: string): Scope => usePermissions()[key] ?? "NONE";

export const useCan = (key: string): boolean => usePermission(key) !== "NONE";
