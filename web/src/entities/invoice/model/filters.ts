// The chips of the list (F8-05). "Abiertas" is everything that is not closed: an invoice waiting for its operators, or
// being counted, has not finished. "Todas" asks for no status at all.
export type StatusChip = "all" | "open" | "closed";

export const OPEN_STATUSES = ["OPEN", "PENDING", "IN_ASSIGNMENT"] as const;

export const openStatuses = (): string[] => [...OPEN_STATUSES];

// The filter the API takes: one status, or several (the query reads a list, as the orders one does).
export interface InvoiceFilters {
	search?: string;
	status?: string[];
}

// A chip read from the address of the page: anything the list does not know is "Todas".
export const statusChip = (value: string | null | undefined): StatusChip => (value === "open" || value === "closed" ? value : "all");

// What the list sends: the box as the search and the chip as the status, both at once (AND).
export function invoiceFilters(input: { search: string; chip: StatusChip }): InvoiceFilters {
	const filters: InvoiceFilters = {};
	const search = input.search.trim();
	if (search) filters.search = search;
	if (input.chip === "closed") filters.status = ["CLOSED"];
	if (input.chip === "open") filters.status = openStatuses();
	return filters;
}
