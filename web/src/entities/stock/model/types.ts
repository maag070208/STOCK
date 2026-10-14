export interface UnitRef {
	code: string;
	label: string | null;
	decimalPlaces: number | null;
}

// One balance above zero: a material in a location (stock.md section 2).
export interface Balance {
	warehouseId: string;
	warehouseCode: string;
	locationId: string;
	locationCode: string;
	aisle: string | null;
	rack: string | null;
	temporary: boolean;
	productId: string;
	productCode: string;
	productName: string;
	unit: UnitRef | null;
	quantity: number;
	updatedAt: string;
	[key: string]: unknown;
}

// A material's stock in a warehouse: the sum of its balances, and in how many locations it is.
export interface ProductTotal {
	warehouseId: string;
	warehouseCode: string;
	productId: string;
	productCode: string;
	productName: string;
	unit: UnitRef | null;
	quantity: number;
	locations: number;
	[key: string]: unknown;
}

// The report of the opening inventory (stock.md section 3). `movements` is set only once it is confirmed.
export interface OpeningReport {
	checksum: string;
	products: number;
	withStock: number;
	total: number;
	controlTotal: number | null;
	controlMatches: boolean | null;
	missing: { row: number; code: string }[];
	rejected: { row: number; field: string; code: string; message: string }[];
	alreadyLoaded: boolean;
	movements: number | null;
}
