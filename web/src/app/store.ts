import { configureStore } from "@reduxjs/toolkit";
import { useDispatch } from "react-redux";
import { authReducer, persistAuth, signedOut, tokensRefreshed } from "@entities/user";
import { setSessionHooks } from "@shared/api/session";
import { toastReducer, toastShown } from "./toast/toast.slice";

// Redux holds only what crosses the whole app: the session and the toasts (web.md section 2).
export const store = configureStore({
	reducer: { auth: authReducer, toast: toastReducer },
});

store.subscribe(() => persistAuth(store.getState().auth));

setSessionHooks({
	token: () => store.getState().auth.token,
	refreshToken: () => store.getState().auth.refreshToken,
	saveTokens: (tokens) => store.dispatch(tokensRefreshed(tokens)),
	endSession: () => store.dispatch(signedOut()),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export const useAppDispatch = () => useDispatch<AppDispatch>();
export const toast = (message: string, type: "success" | "error" | "info" = "success") => store.dispatch(toastShown(message, type));
