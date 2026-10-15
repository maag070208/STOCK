import type { ReportRef } from "./types";

// The warehouses a report has answered so far, for its filter. The reports are scoped before they answer (reports.md
// rule 3), so building the list from their own rows offers nothing the reader cannot see, and needs no other permission.
export function warehouseOptionsOf(...groups: (ReportRef[] | undefined)[]): ReportRef[] {
	const byId = new Map<string, ReportRef>();
	for (const group of groups) {
		for (const ref of group ?? []) {
			if (ref?.id && !byId.has(ref.id)) byId.set(ref.id, ref);
		}
	}
	return [...byId.values()].sort((left, right) => (left.code ?? "").localeCompare(right.code ?? ""));
}
