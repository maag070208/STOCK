import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useRef, useState } from "react";
import { partnerApi, type Partner, type PartnerKind } from "..";

// Picks an active supplier or customer by its code or its name, searching the API as the person types: there are
// hundreds of them, so the list is not brought whole. The value that comes from somewhere else is asked for by its id,
// so it shows at once.
export function PartnerPicker(props: {
	kind?: PartnerKind;
	name: string;
	label: string;
	value: string;
	onChange: (partner: Partner | null) => void;
	error?: string;
	required?: boolean;
	placeholder?: string;
}) {
	const kind = props.kind ?? "suppliers";
	const [options, setOptions] = useState<Partner[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	const search = useCallback(
		async (query: string) => {
			const request = ++latest.current;
			setLoading(true);
			try {
				const term = query.trim();
				const [byName, byCode] = await Promise.all([
					partnerApi(kind).query({ page: 1, limit: 20, filters: { active: "true", ...(term ? { name: term } : {}) } }),
					term ? partnerApi(kind).query({ page: 1, limit: 20, filters: { active: "true", code: term } }) : Promise.resolve({ data: [], total: 0 }),
				]);
				// Only the answer to the last search is shown.
				if (request === latest.current) {
					setOptions([...byCode.data, ...byName.data.filter((partner) => !byCode.data.some((chosen) => chosen.id === partner.id))]);
				}
			} finally {
				if (request === latest.current) setLoading(false);
			}
		},
		[kind],
	);

	useEffect(() => {
		void search("");
	}, [search]);

	// The partner already chosen comes from its id, which the search would not find by itself.
	useEffect(() => {
		if (!props.value) return;
		void partnerApi(kind)
			.get(props.value)
			.then((chosen) => setOptions((current) => (current.some((option) => option.id === chosen.id) ? current : [chosen, ...current])))
			.catch(() => undefined);
	}, [props.value, kind]);

	return (
		<ITSearchSelect
			name={props.name}
			label={props.label}
			placeholder={props.placeholder}
			value={props.value}
			options={options.map((partner) => ({ value: partner.id, label: `${partner.code ?? ""} · ${partner.name ?? ""}` }))}
			onSearch={(query) => void search(query)}
			isLoading={loading}
			onChange={(value) => props.onChange(options.find((partner) => partner.id === String(value)) ?? null)}
			error={props.error}
			touched={!!props.error}
			required={props.required}
		/>
	);
}
