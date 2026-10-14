export interface CatalogLabel {
	label: string;
	description?: string | null;
}

export interface Catalog {
	code: string;
	isSystem: boolean;
	active: boolean;
	items: number;
}

export interface CatalogItem {
	code: string;
	label: string | null;
	description: string | null;
	sortOrder: number;
	active: boolean;
	attributes: Record<string, unknown>;
	labels: Record<string, CatalogLabel>;
	[key: string]: unknown;
}

export interface CatalogItemInput {
	labels?: Record<string, CatalogLabel>;
	sortOrder?: number;
	active?: boolean;
	attributes?: Record<string, unknown>;
}
