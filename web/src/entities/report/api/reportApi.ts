import { downloadPost, post } from "@shared/api/client";
import { serializeFilters, type TableParams } from "@shared/api/table";
import type { InvoiceReportPage, KardexPage, MovementsPage, OrderReportPage, ReportFormat, ReportKind, StockReportPage } from "../model/types";

// Every report takes the table contract and answers it with the totals its screen shows (reports.md section 3). The
// export takes the same filters and answers a file the API builds, never the browser (section 4).
const report = <T>(kind: ReportKind, params: TableParams): Promise<T> =>
	post<T>(`/reports/${kind}`, { ...params, filters: serializeFilters(params.filters) });

export const reportApi = {
	stock: (params: TableParams) => report<StockReportPage>("stock", params),
	kardex: (params: TableParams) => report<KardexPage>("kardex", params),
	movements: (params: TableParams) => report<MovementsPage>("movements", params),
	orders: (params: TableParams) => report<OrderReportPage>("orders", params),
	invoices: (params: TableParams) => report<InvoiceReportPage>("invoices", params),
	// The file of a report, with the name the API gave it (reports.md section 4). It needs reports.export.
	exportFile: (kind: ReportKind, format: ReportFormat, filters: TableParams["filters"]) =>
		downloadPost(`/reports/${kind}/export`, { format, filters: serializeFilters(filters) }),
};
