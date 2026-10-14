import { del, get, patch, post } from "@shared/api/client";
import { tableRequest, type TableParams } from "@shared/api/table";
import type { ImportReport, Product, ProductInput } from "../model/types";

const form = (file: File, fields: Record<string, string> = {}) => {
	const data = new FormData();
	data.append("file", file);
	for (const [name, value] of Object.entries(fields)) data.append(name, value);
	return data;
};

export const productApi = {
	query: (params: TableParams) => tableRequest<Product>("/products/query", params),
	get: (id: string) => get<Product>(`/products/${id}`),
	create: (input: ProductInput & { code: string }) => post<Product>("/products", input),
	update: (id: string, changes: ProductInput) => patch<Product>(`/products/${id}`, changes),
	deactivate: (id: string) => del<Product>(`/products/${id}`),
	reactivate: (id: string) => post<Product>(`/products/${id}/reactivate`),
	// The legacy file: a dry run that writes nothing, then the confirmation of the same file with the dry run's checksum.
	importDryRun: (file: File) => post<ImportReport>("/products/import/dry-run", form(file)),
	importConfirm: (file: File, checksum: string) => post<ImportReport>("/products/import", form(file, { checksum })),
};
