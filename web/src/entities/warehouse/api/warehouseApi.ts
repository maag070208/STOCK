import { del, get, patch, post } from "@shared/api/client";
import { tableRequest, type TableParams } from "@shared/api/table";
import type { AisleGroup, Location, RackInput, Warehouse } from "../model/types";

export const warehouseApi = {
	query: (params: TableParams) => tableRequest<Warehouse>("/warehouses/query", params),
	get: (id: string) => get<Warehouse>(`/warehouses/${id}`),
	groups: (id: string) => get<AisleGroup[]>(`/warehouses/${id}/location-groups`),
	create: (input: { code: string; name: string }) => post<Warehouse>("/warehouses", input),
	update: (id: string, changes: { name: string }) => patch<Warehouse>(`/warehouses/${id}`, changes),
	deactivate: (id: string) => del<Warehouse>(`/warehouses/${id}`),
	reactivate: (id: string) => post<Warehouse>(`/warehouses/${id}/reactivate`),
};

export const locationApi = {
	query: (params: TableParams) => tableRequest<Location>("/locations/query", params),
	createRack: (input: RackInput) => post<{ locations: Location[] }>("/locations/racks", input),
	deactivate: (id: string) => del<Location>(`/locations/${id}`),
	reactivate: (id: string) => post<Location>(`/locations/${id}/reactivate`),
};
