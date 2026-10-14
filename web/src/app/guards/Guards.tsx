import { useEffect, type ReactNode } from "react";
import { useSelector } from "react-redux";
import { Navigate, useLocation } from "react-router-dom";
import { loadMe, useCan, type SessionUser } from "@entities/user";
import i18n from "@shared/i18n";
import { useAppDispatch, type RootState } from "@app/store";
import { ForbiddenPage } from "@pages/forbidden/ForbiddenPage";
import { sessionRedirect } from "./sessionRoute";

// Sends the person where their session allows (sessionRedirect), and keeps their access fresh:
// /auth/me is read when the app opens, so a change by an administrator shows without signing in again.
export function SessionGate({ children }: { children: ReactNode }) {
	const session = useSelector((state: RootState) => state.auth);
	const dispatch = useAppDispatch();
	const location = useLocation();

	useEffect(() => {
		if (session.token) void dispatch(loadMe());
	}, [session.token, dispatch]);

	useLanguageOf(session.user);

	const redirect = sessionRedirect(session, location.pathname);
	if (redirect) return <Navigate to={redirect} replace />;
	return <>{children}</>;
}

// The interface follows the language saved on the person's profile.
function useLanguageOf(user: SessionUser | null) {
	useEffect(() => {
		if (user?.language && user.language !== i18n.language) void i18n.changeLanguage(user.language);
	}, [user?.language]);
}

// A screen that needs a permission. Without it, the 403 page.
export function RequirePermission({ anyOf, children }: { anyOf: readonly string[]; children: ReactNode }) {
	const allowed = anyOf.map(useCan).some(Boolean);
	return allowed ? <>{children}</> : <ForbiddenPage />;
}
