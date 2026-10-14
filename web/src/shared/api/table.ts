import type { ColumnFilters } from "@axzydev/axzy_ui_system";
import { post } from "./client";

// The table contract of the API (api.md section 2): POST /{resource}/query with { page, limit, filters, sort }.
export interface TableParams {
	page: number;
	limit: number;
	filters: ColumnFilters;
	sort?: { key: string; direction: "asc" | "desc" };
}

export interface TablePage<T> {
	data: T[];
	total: number;
}

type FilterValue = string | number | boolean | [string | null, string | null];

const pad = (n: number, size = 2) => String(n).padStart(size, "0");

// An instant in ISO 8601 with the local offset, so the API gets the exact moment the person meant.
function localIso(date: Date): string {
	const offset = -date.getTimezoneOffset();
	const sign = offset >= 0 ? "+" : "-";
	const abs = Math.abs(offset);
	return (
		`${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
		`T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}` +
		`${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
	);
}

const startOfDay = (d: Date) => localIso(new Date(d.getFullYear(), d.getMonth(), d.getDate()));
const endOfDay = (d: Date) => localIso(new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999));

// A date filter is the whole local day; a range runs from the start of its first day to the end of its last.
export function serializeFilters(filters: ColumnFilters): Record<string, FilterValue> {
	const out: Record<string, FilterValue> = {};
	for (const [key, value] of Object.entries(filters)) {
		if (value instanceof Date) out[key] = [startOfDay(value), endOfDay(value)];
		else if (Array.isArray(value)) {
			const [from, to] = value as [Date | null, Date | null];
			if (from || to) out[key] = [from ? startOfDay(from) : null, to ? endOfDay(to) : null];
		} else if (value !== undefined && value !== null && value !== "") out[key] = value as FilterValue;
	}
	return out;
}

export const tableRequest = <T>(path: string, params: TableParams): Promise<TablePage<T>> =>
	post<TablePage<T>>(path, { ...params, filters: serializeFilters(params.filters) });
