import { type Column, ITTable } from "@axzydev/axzy_ui_system";

// A table of the rows a screen already has, built with the kit: one page with every row and the table view, because a
// list the screen holds itself is not paginated (web.md section 8). The kit's table takes its page size once, so it is
// mounted again when the number of rows changes: that is what makes every row show up once the data has arrived.
export function PlainTable<T extends Record<string, unknown>>(props: {
	columns: Column<T>[];
	data: T[];
	size?: "sm" | "md" | "lg";
	onRowClick?: (row: T) => void;
}) {
	const rows = Math.max(props.data.length, 1);
	return (
		<ITTable<T>
			key={`plain-table-${rows}`}
			columns={props.columns}
			data={props.data}
			size={props.size}
			onRowClick={props.onRowClick ? (row: T) => props.onRowClick?.(row) : undefined}
			// The kit's table offers the cards view and a page size; a screen that holds all its rows needs neither.
			showViewToggle={false}
			footerClassName="sf:hidden"
			defaultView="table"
			defaultItemsPerPage={rows}
			itemsPerPageOptions={[rows]}
			containerClassName="sf:overflow-x-auto"
		/>
	);
}
