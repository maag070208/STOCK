import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { warehouseApi, type Warehouse } from "..";

// Picks an active warehouse by its code or its name, searching the API as the person types: an installation can hold
// hundreds of them, so the list is not brought whole. The value that comes from somewhere else is asked for by its id,
// so it shows at once.
export function WarehousePicker(props: { name: string; label: string; value: string; onChange: (warehouseId: string) => void; error?: string; required?: boolean; placeholder?: string }) {
	const [options, setOptions] = useState<Warehouse[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	async function search(query: string) {
		const request = ++latest.current;
		setLoading(true);
		try {
			const term = query.trim();
			const [byCode, byName] = await Promise.all([
				warehouseApi.query({ page: 1, limit: 20, filters: { active: "true", ...(term ? { code: term } : {}) } }),
				term ? warehouseApi.query({ page: 1, limit: 20, filters: { active: "true", name: term } }) : Promise.resolve({ data: [], total: 0 }),
			]);
			if (request === latest.current) setOptions([...byCode.data, ...byName.data.filter((warehouse) => !byCode.data.some((chosen) => chosen.id === warehouse.id))]);
		} finally {
			if (request === latest.current) setLoading(false);
		}
	}

	useEffect(() => {
		void search("");
	}, []);

	// A warehouse already chosen comes from its id, which the search would not find by itself.
	useEffect(() => {
		if (!props.value) return;
		void warehouseApi
			.get(props.value)
			.then((chosen) => setOptions((current) => (current.some((option) => option.id === chosen.id) ? current : [chosen, ...current])))
			.catch(() => undefined);
	}, [props.value]);

	return (
		<ITSearchSelect
			name={props.name}
			label={props.label}
			placeholder={props.placeholder}
			value={props.value}
			options={options.map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code ?? ""} · ${warehouse.name ?? ""}` }))}
			onSearch={(query) => void search(query)}
			isLoading={loading}
			onChange={(value) => props.onChange(String(value))}
			error={props.error}
			touched={!!props.error}
			required={props.required}
		/>
	);
}
