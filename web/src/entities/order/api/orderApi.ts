import { download, get, post, put } from "@shared/api/client";
import { tableRequest, type TablePage, type TableParams } from "@shared/api/table";
import { newId } from "@shared/lib/newId";
import type { AvailabilityItem, Order, OrderAlert, OrderInput, OrderOperator, Ref, ReturnInput, TransferInput } from "../model/types";

// The orders of the warehouse (order-fulfillment.md): creation with its reservation, the reads, and the actions the
// office and the manager take on an order.
export const orderApi = {
	query: (params: TableParams) => tableRequest<Order>("/orders/query", params),
	// The orders of a warehouse that still need something: the targets of a reassignment (section 8). The status filter
	// takes one code or several, and the table helper only sends one, so the body is written here.
	openOf: (warehouseId: string) =>
		post<TablePage<Order>>("/orders/query", {
			page: 1,
			limit: 200,
			filters: { warehouseId, status: ["CREATED", "FULFILLING"] },
			sort: { key: "dueDate", direction: "asc" },
		}),
	get: (id: string) => get<Order>(`/orders/${id}`),
	// The key makes a retry of the same request create one order (api.md section 2). One key per form submission.
	create: (input: OrderInput, key: string = newId()) => post<Order>("/orders", input, { "Idempotency-Key": key }),
	assign: (id: string, operatorId: string) => put<Order>(`/orders/${id}/assignment`, { operatorId }),
	// The operators an order of this warehouse can be assigned to.
	operators: (warehouseId: string) => get<OrderOperator[]>("/orders/operators", { warehouseId }),
	// What the warehouse has of these materials, for the lines of a new order.
	availability: (warehouseId: string, productIds: string[]) => post<{ items: AvailabilityItem[] }>("/orders/availability", { warehouseId, productIds }),
	// The receipt of a validated order, as a private file.
	document: (id: string) => download(`/orders/${id}/document`),
	alerts: () => get<{ alerts: OrderAlert[] }>("/orders/alerts"),
	validate: (id: string, note?: string) => post<Order>(`/orders/${id}/validation`, { note: note?.trim() ? note.trim() : null }),
	acceptDifference: (id: string, note: string) => post<Order>(`/orders/${id}/accept-difference`, { note }),
	cancel: (id: string) => post<Order>(`/orders/${id}/cancellation`),
	reassignOperator: (id: string, operatorId: string, note: string) => post<Order>(`/orders/${id}/reassignments/operator`, { operatorId, note }),
	transfer: (id: string, input: TransferInput) => post<Order>(`/orders/${id}/reassignments/transfer`, input),
	returnStock: (id: string, input: ReturnInput) => post<Order>(`/orders/${id}/reassignments/return`, input),
	// The warehouses a new order may leave from, with orders.create: whoever orders does not need the invoice
	// permission to see them.
	warehouseOptions: () => get<Ref[]>("/orders/warehouse-options"),
};
