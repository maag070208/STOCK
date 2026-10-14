export interface Warehouse {
	id: string;
	code: string;
	name: string;
	active: boolean;
	temporaryLocation: { id: string; code: string } | null;
	createdAt: string;
	[key: string]: unknown;
}

export interface Location {
	id: string;
	warehouseId: string;
	warehouseCode: string;
	code: string;
	aisle: string | null;
	rack: string | null;
	level: string | null;
	temporary: boolean;
	active: boolean;
	[key: string]: unknown;
}

// The groups of a warehouse's locations: each aisle with its racks, and how many locations each rack has.
export interface AisleGroup {
	aisle: string;
	racks: { rack: string; locations: number; active: number }[];
}

export interface RackInput {
	warehouseId: string;
	aisle: string;
	rack: string;
	levels: number;
}
