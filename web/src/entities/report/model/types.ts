// The reports the owner reads (docs/modules/reports.md). They read only: no report changes stock. Every report answers
// the table contract plus the totals its screen shows, and the same rows feed its export.

export interface ReportRef {
	id: string;
	code: string | null;
	name: string | null;
}

export interface ReportUnit {
	code: string | null;
	label: string | null;
	decimalPlaces: number | null;
}

// One total of a report: a group (a warehouse, an aisle and rack, a status, a movement type) and what its rows add up to.
export interface ReportTotal {
	key: string;
	label: string;
	quantity: number;
	rows: number;
}

// The table contract of the API (api.md section 2), which every report answers.
export interface ReportPage<T> {
	data: T[];
	total: number;
	page: number;
	pageIndex: number;
	totalPages: number;
	totalCount: number;
	limit: number;
	hasPreviousPage: boolean;
	hasNextPage: boolean;
}

// One balance of the current stock report (reports.md section 3).
export interface StockReportRow {
	warehouse: ReportRef;
	aisle: string | null;
	rack: string | null;
	location: ReportRef;
	product: ReportRef;
	unit: ReportUnit | null;
	quantity: number;
	minimum: number;
	// Set when the material is below its minimum over the warehouses the reader reaches.
	shortfall: number | null;
	// The group by aisle and rack, as the API wrote it: "K05".
	group: string;
	[key: string]: unknown;
}

export interface StockReportPage extends ReportPage<StockReportRow> {
	quantity: number;
	warehouseTotals: ReportTotal[];
	groupTotals: ReportTotal[];
}

// One movement, as the kardex and the movements report list it.
export interface MovementReportRow {
	seq: number;
	occurredAt: string;
	type: string | null;
	typeLabel: string | null;
	warehouse: ReportRef;
	location: ReportRef;
	product: ReportRef;
	unit: ReportUnit | null;
	quantity: number;
	incoming: boolean;
	// The running balance of the location, and of the material in the warehouse: the API computed them.
	runningLocation: number | null;
	runningWarehouse: number | null;
	user: ReportRef | null;
	reason: string | null;
	sourceType: string | null;
	sourceId: string | null;
	[key: string]: unknown;
}

export interface KardexProduct {
	product: ReportRef;
	warehouse: ReportRef;
	unit: ReportUnit | null;
	closing: number;
}

export interface KardexPage extends ReportPage<MovementReportRow> {
	// Null while the screen has not asked for a material.
	product: KardexProduct | null;
}

export interface MovementsPage extends ReportPage<MovementReportRow> {
	incoming: number;
	outgoing: number;
	typeTotals: ReportTotal[];
}

// One line of an order as the orders report lists it.
export interface OrderReportRow {
	order: ReportRef;
	warehouse: ReportRef;
	customer: ReportRef;
	status: string | null;
	statusLabel: string | null;
	dueDate: string;
	product: ReportRef;
	asked: number;
	picked: number;
	movedOut: number;
	movedIn: number;
	createdAt: string;
	validatedAt: string | null;
	[key: string]: unknown;
}

export interface OrderReportPage extends ReportPage<OrderReportRow> {
	asked: number;
	picked: number;
	movedOut: number;
	movedIn: number;
	statusTotals: ReportTotal[];
}

// One invoice line as the entries report lists it.
export interface InvoiceReportRow {
	invoice: ReportRef;
	supplier: ReportRef;
	warehouse: ReportRef;
	status: string | null;
	statusLabel: string | null;
	invoiceDate: string;
	product: ReportRef;
	expected: number;
	counted: number;
	difference: number;
	placed: number;
	[key: string]: unknown;
}

export interface InvoiceReportPage extends ReportPage<InvoiceReportRow> {
	expected: number;
	counted: number;
	difference: number;
	placed: number;
	statusTotals: ReportTotal[];
}

export type ReportKind = "stock" | "kardex" | "movements" | "orders" | "invoices";

export type ReportFormat = "XLSX" | "PDF";
