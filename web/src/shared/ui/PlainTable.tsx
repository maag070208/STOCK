import { type Column, ITTable } from "@axzydev/axzy_ui_system";
import { cardBreakpoint, tableHeight } from "./tableLayout";

// A table of the rows a screen already has, built with the kit and configured like every other table (web.md section 8):
// 25 rows a page, the page sizes of the app, the compact density and virtualization. It exists so every table of the app
// is built and configured the same way, and it is mounted again when the number of rows changes, because the kit's table
// takes its page size once.
export function PlainTable<T extends Record<string, unknown>>(props: {
	columns: Column<T>[];
	data: T[];
	size?: "sm" | "md" | "lg";
	onRowClick?: (row: T) => void;
	// A table of a fixed, short list (the roles of the access console) does not need the virtualizer, and a screen
	// reader and a test read all of its rows at once.
	virtualized?: boolean;
}) {
	const rows = props.data.length;
	return (
		<ITTable<T>
			key={`plain-table-${rows}`}
			columns={props.columns}
			data={props.data}
			size={props.size}
			onRowClick={props.onRowClick ? (row: T) => props.onRowClick?.(row) : undefined}
			// Every table of the app is configured the same way (web.md section 8).
			showViewToggle={false}
			autoCardBreakpoint={cardBreakpoint()}
			defaultItemsPerPage={25}
			itemsPerPageOptions={[25, 50, 100]}
			layout="fixed"
			density="compact"
			virtualized={props.virtualized ?? true}
			virtualizedMaxHeight={props.virtualized === false ? undefined : tableHeight()}
			rowHeight={50}
			containerClassName="sf:overflow-x-auto"
		/>
	);
}
