import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useRef, useState } from "react";
import { warehouseApi, type Warehouse } from "..";

// Picks a warehouse by its code or its name. When the screen already has the warehouses it may see — they come from the
// module's own options endpoint, which a person of the office can read — the picker searches them right there; otherwise
// it asks the API as the person types, which needs `locations.view`. An installation holds hundreds, so the list is never
// brought whole.
export function WarehousePicker(props: {
	name: string;
	label: string;
	value: string;
	options?: { id: string; code?: string | null; name?: string | null }[];
	onChange: (warehouseId: string) => void;
	error?: string;
	required?: boolean;
	placeholder?: string;
}) {
	const given = props.options;
	const [loaded, setLoaded] = useState<Warehouse[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	const search = useCallback(async (query: string) => {
		const request = ++latest.current;
		setLoading(true);
		try {
			const term = query.trim();
			const [byCode, byName] = await Promise.all([
				warehouseApi.query({ page: 1, limit: 20, filters: { active: "true", ...(term ? { code: term } : {}) } }),
				term ? warehouseApi.query({ page: 1, limit: 20, filters: { active: "true", name: term } }) : Promise.resolve({ data: [], total: 0 }),
			]);
			if (request === latest.current) setLoaded([...byCode.data, ...byName.data.filter((warehouse) => !byCode.data.some((chosen) => chosen.id === warehouse.id))]);
		} finally {
			if (request === latest.current) setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (given) return;
		void search("");
	}, [given, search]);

	// A warehouse already chosen comes from its id, which the search would not find by itself.
	useEffect(() => {
		if (given || !props.value) return;
		void warehouseApi
			.get(props.value)
			.then((chosen) => setLoaded((current) => (current.some((option) => option.id === chosen.id) ? current : [chosen, ...current])))
			.catch(() => undefined);
	}, [given, props.value]);

	const options = (given ?? loaded).map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code ?? ""} · ${warehouse.name ?? ""}` }));
	return (
		<ITSearchSelect
			name={props.name}
			label={props.label}
			placeholder={props.placeholder}
			value={props.value}
			options={options}
			// With a list of its own, the picker filters it; without one, it asks the API as the person types.
			onSearch={given ? undefined : (query) => void search(query)}
			isLoading={loading}
			onChange={(value) => props.onChange(String(value))}
			error={props.error}
			touched={!!props.error}
			required={props.required}
		/>
	);
}
