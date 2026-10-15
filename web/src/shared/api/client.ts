import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import i18n from "@shared/i18n";
import { fileNameOf } from "@shared/lib/fileName";
import { API_URL, CHANNEL, CHANNEL_HEADER } from "./config";
import { endCurrentSession, sessionRefreshToken, sessionToken, updateSessionTokens } from "./session";

export interface ApiErrorDetail {
	field: string;
	code: string;
	message: string;
}

// An error of the API, normalized: its status, its code, the message in the person's language, and the fields that failed.
export class ApiError extends Error {
	readonly status: number;
	readonly code: string;
	readonly details: ApiErrorDetail[];

	constructor(status: number, code: string, message: string, details: ApiErrorDetail[] = []) {
		super(message);
		this.name = "ApiError";
		this.status = status;
		this.code = code;
		this.details = details;
	}

	// The message of each failed field, by field name.
	fieldErrors(): Record<string, string> {
		return Object.fromEntries(this.details.map((detail) => [detail.field, detail.message]));
	}
}

const http = axios.create({ baseURL: API_URL, timeout: 20000, headers: { [CHANNEL_HEADER]: CHANNEL } });

http.interceptors.request.use((config) => {
	const token = sessionToken();
	if (token) config.headers.Authorization = `Bearer ${token}`;
	// The API answers in the language the person is using.
	config.headers["Accept-Language"] = i18n.language;
	return config;
});

// A single refresh at a time: requests that fail together wait for the same one.
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
	const refreshToken = sessionRefreshToken();
	if (!refreshToken) return null;
	refreshing ??= axios
		.post(`${API_URL}/auth/refresh`, { refreshToken }, { headers: { [CHANNEL_HEADER]: CHANNEL } })
		.then(({ data }) => {
			updateSessionTokens({ token: data.token, refreshToken: data.refreshToken });
			return data.token as string;
		})
		.catch(() => null)
		.finally(() => {
			refreshing = null;
		});
	return refreshing;
}

type Retriable = InternalAxiosRequestConfig & { retried?: boolean };

http.interceptors.response.use(
	(response) => response,
	async (error: AxiosError<{ code?: string; message?: string; details?: ApiErrorDetail[] }>) => {
		const original = error.config as Retriable | undefined;
		const status = error.response?.status;
		// An expired access token is renewed once, and the request is sent again.
		if (status === 401 && original && !original.retried && !original.url?.includes("/auth/")) {
			original.retried = true;
			const token = await refreshAccessToken();
			if (token) {
				original.headers.Authorization = `Bearer ${token}`;
				return http(original);
			}
			endCurrentSession();
		}
		// A deactivated account ends the session, and the message says why.
		if (error.response?.data?.code === "ACCOUNT_DEACTIVATED") endCurrentSession();
		throw toApiError(error);
	},
);

function toApiError(error: AxiosError<{ code?: string; message?: string; details?: ApiErrorDetail[] }>): ApiError {
	const body = error.response?.data;
	if (!error.response) return new ApiError(0, "NETWORK", i18n.t("errors.network"));
	return new ApiError(error.response.status, body?.code ?? "UNKNOWN", body?.message ?? i18n.t("errors.unexpected"), body?.details ?? []);
}

export const get = <T>(path: string, params?: Record<string, unknown>) => http.get<T>(path, { params }).then((r) => r.data);
// `headers` adds request headers, such as the Idempotency-Key of a creation (api.md section 2).
export const post = <T>(path: string, body?: unknown, headers?: Record<string, string>) => http.post<T>(path, body, { headers }).then((r) => r.data);
export const put = <T>(path: string, body?: unknown) => http.put<T>(path, body).then((r) => r.data);
export const patch = <T>(path: string, body?: unknown) => http.patch<T>(path, body).then((r) => r.data);
export const del = <T>(path: string) => http.delete<T>(path).then((r) => r.data);
// A private file the API answers, such as the receipt of an order: the bytes, with the session and the language.
export const download = (path: string) => http.get<Blob>(path, { responseType: "blob" }).then((r) => r.data);

// A private file the API answers to a POST, such as the export of a report: the bytes, and the name the API gave them
// in the response (reports.md section 4). The browser never builds the file.
export const downloadPost = (path: string, body?: unknown): Promise<{ blob: Blob; fileName: string }> =>
	http.post<Blob>(path, body, { responseType: "blob" }).then((response) => ({
		blob: response.data,
		fileName: fileNameOf(response.headers["content-disposition"] as string | undefined),
	}));
