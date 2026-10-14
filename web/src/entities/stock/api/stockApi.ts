import { post } from "@shared/api/client";
import { tableRequest, type TableParams } from "@shared/api/table";
import type { Balance, OpeningReport, ProductTotal } from "../model/types";

const form = (file: File, fields: Record<string, string>) => {
	const data = new FormData();
	data.append("file", file);
	for (const [name, value] of Object.entries(fields)) data.append(name, value);
	return data;
};

export const stockApi = {
	balances: (params: TableParams) => tableRequest<Balance>("/stock/balances/query", params),
	products: (params: TableParams) => tableRequest<ProductTotal>("/stock/products/query", params),
	// The opening inventory of a warehouse, from the legacy file: a dry run first, then the same file with its checksum.
	openingDryRun: (file: File, warehouseId: string) => post<OpeningReport>("/stock/opening/dry-run", form(file, { warehouseId })),
	openingConfirm: (file: File, warehouseId: string, checksum: string) => post<OpeningReport>("/stock/opening", form(file, { warehouseId, checksum })),
};
