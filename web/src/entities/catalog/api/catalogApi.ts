import { get, patch, post } from "@shared/api/client";
import type { Catalog, CatalogItem, CatalogItemInput } from "../model/types";

export const catalogApi = {
	list: () => get<Catalog[]>("/catalogs"),
	// Active values in order, labeled in the language in use. With `all`, the inactive ones too.
	items: (code: string, all = false) => get<CatalogItem[]>(`/catalogs/${code}/items`, all ? { all: true } : undefined),
	create: (code: string) => post<Catalog>("/catalogs", { code }),
	createItem: (catalog: string, item: CatalogItemInput & { code: string }) => post<CatalogItem>(`/catalogs/${catalog}/items`, item),
	updateItem: (catalog: string, code: string, changes: CatalogItemInput) => patch<CatalogItem>(`/catalogs/${catalog}/items/${code}`, changes),
};
