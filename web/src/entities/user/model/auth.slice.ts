import { createAsyncThunk, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import type { LoginResult, SessionUser, Tokens } from "./types";

interface AuthState {
	token: string | null;
	refreshToken: string | null;
	user: SessionUser | null;
	// The /auth/me request in flight. A newer change to the user (userLoaded) cancels it, so an older answer never wins.
	meRequest?: string | null;
}

const STORAGE_KEY = "stockflow.auth";
const empty: AuthState = { token: null, refreshToken: null, user: null };

// The session survives a reload. The permissions in it are refreshed from /auth/me when the app opens.
function load(): AuthState {
	try {
		const raw = window.localStorage.getItem(STORAGE_KEY);
		return raw ? { ...empty, ...(JSON.parse(raw) as AuthState) } : empty;
	} catch {
		return empty;
	}
}

export function persistAuth(state: AuthState) {
	try {
		if (state.token) window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: state.token, refreshToken: state.refreshToken, user: state.user }));
		else window.localStorage.removeItem(STORAGE_KEY);
	} catch {
		// Without storage the session lasts for this page only.
	}
}

export const login = createAsyncThunk("auth/login", ({ username, password }: { username: string; password: string }) =>
	authApi.login(username.trim(), password),
);

export const loadMe = createAsyncThunk("auth/me", () => authApi.me());

const slice = createSlice({
	name: "auth",
	initialState: load,
	reducers: {
		signedIn(state, action: PayloadAction<LoginResult>) {
			state.token = action.payload.token;
			state.refreshToken = action.payload.refreshToken;
			state.user = action.payload.user;
		},
		tokensRefreshed(state, action: PayloadAction<Tokens>) {
			state.token = action.payload.token;
			state.refreshToken = action.payload.refreshToken;
		},
		userLoaded(state, action: PayloadAction<SessionUser>) {
			state.user = action.payload;
			state.meRequest = null;
		},
		signedOut() {
			return empty;
		},
	},
	extraReducers: (builder) => {
		builder.addCase(login.fulfilled, (state, action) => {
			state.token = action.payload.token;
			state.refreshToken = action.payload.refreshToken;
			state.user = action.payload.user;
		});
		builder.addCase(loadMe.pending, (state, action) => {
			state.meRequest = action.meta.requestId;
		});
		builder.addCase(loadMe.fulfilled, (state, action) => {
			if (state.meRequest !== action.meta.requestId) return;
			state.user = action.payload;
			state.meRequest = null;
		});
	},
});

export const { signedIn, tokensRefreshed, userLoaded, signedOut } = slice.actions;
export const authReducer = slice.reducer;
export type { AuthState };
