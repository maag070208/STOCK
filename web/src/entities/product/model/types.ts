export interface Product {
	id: string;
	code: string;
	name: string;
	barcode: string;
	unit: { code: string; label: string | null; decimalPlaces: number | null };
	legacyId: number | null;
	active: boolean;
	createdAt: string;
	[key: string]: unknown;
}

export interface ProductInput {
	name: string;
	unit: string;
	// Blank: the product's code is its barcode.
	barcode: string;
}

export interface RejectedRow {
	row: number;
	field: string;
	code: string;
	message: string;
}

// The report of the legacy file's import (products.md section 8). `created` is set only once it is confirmed.
export interface ImportReport {
	checksum: string;
	accepted: number;
	newProducts: number;
	existingProducts: number;
	rejected: RejectedRow[];
	stockTotal: number;
	controlTotal: number | null;
	controlMatches: boolean | null;
	created: number | null;
}
