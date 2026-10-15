import type { KardexPage, MovementReportRow } from "./types";

// What the kardex table shows of one movement. The running balances are the ones the API computed, as they came: the
// balance of a movement depends on every movement before it in the warehouse, and only the report can add that up
// (reports.md section 3). The screen never does the arithmetic.
export interface KardexRow {
	// The movement's sequence: unique, and how the table keys its rows.
	key: string;
	occurredAt: string;
	type: string;
	location: string;
	// Null when the movement did not go that way.
	incoming: number | null;
	outgoing: number | null;
	runningLocation: number | null;
	runningWarehouse: number | null;
	user: string;
	reason: string;
	[key: string]: unknown;
}

const nameOf = (movement: MovementReportRow): string =>
	movement.location.code ?? movement.location.name ?? "";

const personOf = (movement: MovementReportRow): string =>
	movement.user?.name ?? movement.user?.code ?? "";

// The rows of the kardex, in the order the API sent them (oldest first).
export const kardexRows = (page: KardexPage | null): KardexRow[] =>
	(page?.data ?? []).map((movement) => ({
		key: String(movement.seq),
		occurredAt: movement.occurredAt,
		type: movement.typeLabel ?? movement.type ?? "",
		location: nameOf(movement),
		incoming: movement.incoming ? movement.quantity : null,
		outgoing: movement.incoming ? null : movement.quantity,
		runningLocation: movement.runningLocation,
		runningWarehouse: movement.runningWarehouse,
		user: personOf(movement),
		reason: movement.reason ?? "",
	}));

// The closing balance the report answered for the material: the last running balance of the warehouse, and never one the
// screen adds up from the rows it happens to hold.
export const kardexClosing = (page: KardexPage | null): number | null =>
	page?.product?.closing ?? null;
