// The HTTP client reads and updates the session through these hooks. The app layer connects them to the store,
// so shared code never imports the store.
type Tokens = { token: string; refreshToken: string };

let readToken: () => string | null = () => null;
let readRefreshToken: () => string | null = () => null;
let saveTokens: (tokens: Tokens) => void = () => {};
let endSession: () => void = () => {};

export function setSessionHooks(hooks: {
	token: () => string | null;
	refreshToken: () => string | null;
	saveTokens: (tokens: Tokens) => void;
	endSession: () => void;
}) {
	readToken = hooks.token;
	readRefreshToken = hooks.refreshToken;
	saveTokens = hooks.saveTokens;
	endSession = hooks.endSession;
}

export const sessionToken = () => readToken();
export const sessionRefreshToken = () => readRefreshToken();
export const updateSessionTokens = (tokens: Tokens) => saveTokens(tokens);
export const endCurrentSession = () => endSession();
