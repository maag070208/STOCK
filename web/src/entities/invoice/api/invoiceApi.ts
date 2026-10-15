import { del, get, patch, post, put } from "@shared/api/client";
import { tableRequest, type TablePage, type TableParams } from "@shared/api/table";
import type { InvoiceFilters } from "../model/filters";
import { newId } from "@shared/lib/newId";
import type { Assignment, Discrepancy, Invoice, InvoiceInput, InvoiceLine, Ref, StaffMember } from "../model/types";

// What the list asks for: the page, and the filters of the search box and the chips. The status travels as a list — the
// query reads one code or several — so the body is written here instead of going through the table helper (F8-05).
export interface InvoiceListQuery {
	page: number;
	limit: number;
	filters: InvoiceFilters;
	sort?: { key: string; direction: "asc" | "desc" };
}

export const invoiceApi = {
	list: (query: InvoiceListQuery) => post<TablePage<Invoice>>("/invoices/query", query),
	get: (id: string) => get<Invoice>(`/invoices/${id}`),
	// The key makes a retry of the same request create one invoice (api.md section 2). One key per form submission.
	create: (input: InvoiceInput, key: string = newId()) => post<Invoice>("/invoices", input, { "Idempotency-Key": key }),
	warehouseOptions: () => get<Ref[]>("/invoices/warehouse-options"),
	addLine: (id: string, line: { productId: string; quantity: number }) => post<InvoiceLine>(`/invoices/${id}/lines`, line),
	updateLine: (id: string, lineId: string, quantity: number) => patch<InvoiceLine>(`/invoices/${id}/lines/${lineId}`, { quantity }),
	removeLine: (id: string, lineId: string) => del<void>(`/invoices/${id}/lines/${lineId}`),
	setAssignments: (id: string, lineId: string, assignments: { operatorId: string; quantity: number }[]) =>
		put<InvoiceLine>(`/invoices/${id}/lines/${lineId}/assignments`, { assignments }),
	// The fast flow closes by hand what never had operators (F8-03).
	close: (id: string) => post<Invoice>(`/invoices/${id}/closure`),
};

export const staffApi = {
	list: (warehouseId: string) => get<StaffMember[]>(`/warehouses/${warehouseId}/staff`),
	candidates: (warehouseId: string) => get<StaffMember[]>(`/warehouses/${warehouseId}/staff/candidates`),
	replace: (warehouseId: string, userIds: string[]) => put<StaffMember[]>(`/warehouses/${warehouseId}/staff`, { userIds }),
};

export const receivingApi = {
	discrepancies: (params: TableParams) => tableRequest<Discrepancy>("/receipts/discrepancies/query", params),
	adjust: (assignmentId: string, note: string) => post<Assignment>(`/receipts/assignments/${assignmentId}/adjustment`, { note }),
};
