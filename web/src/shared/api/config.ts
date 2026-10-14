declare global {
	interface Window {
		__APP_CONFIG__?: { API_URL?: string };
	}
}

// web.md section 3: the runtime config, then the build's environment, then the same origin.
export const API_URL: string = window.__APP_CONFIG__?.API_URL ?? import.meta.env.VITE_API_URL ?? "/api/v1";

// Every request says it comes from the web. The API checks web.access for it.
export const CHANNEL_HEADER = "X-Client-Channel";
export const CHANNEL = "web";
