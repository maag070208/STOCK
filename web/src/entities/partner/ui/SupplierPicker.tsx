import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { partnerApi, type Partner } from "..";

// Picks an active supplier by its code or its name, searching the API as the person types: there are hundreds of them,
// so the list is not brought whole. The value that comes from the session is asked for by its id, so it shows at once.
export function SupplierPicker(props: { name: string; label: string; value: string; onChange: (supplier: Partner | null) => void; error?: string; required?: boolean; placeholder?: string }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [options, setOptions] = useState<Partner[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	async function search(query: string) {
		const request = ++latest.current;
		setLoading(true);
		try {
			const filters = { active: "true", ...(query.trim() ? { name: query.trim() } : {}) };
			const [byName, byCode] = await Promise.all([
				partnerApi("suppliers").query({ page: 1, limit: 20, filters }),
				query.trim() ? partnerApi("suppliers").query({ page: 1, limit: 20, filters: { active: "true", code: query.trim() } }) : Promise.resolve({ data: [], total: 0 }),
			]);
			// Only the answer to the last search is shown.
			if (request === latest.current) setOptions([...byCode.data, ...byName.data.filter((supplier) => !byCode.data.some((chosen) => chosen.id === supplier.id))]);
		} finally {
			if (request === latest.current) setLoading(false);
		}
	}

	useEffect(() => {
		void search("");
	}, []);

	// The supplier remembered from the last invoice is shown by its id, which the search would not find by itself.
	useEffect(() => {
		if (!props.value) return;
		void partnerApi("suppliers")
			.get(props.value)
			.then((supplier) => setOptions((current) => [supplier, ...current.filter((option) => option.id !== supplier.id)]))
			.catch(() => undefined);
	}, [props.value]);

	return (
		<ITSearchSelect
			name={props.name}
			label={props.label}
			placeholder={props.placeholder ?? t("modal.supplierSearch")}
			value={props.value}
			options={options.map((supplier) => ({ value: supplier.id, label: `${supplier.code ?? ""} · ${supplier.name ?? ""}` }))}
			onSearch={(query) => void search(query)}
			isLoading={loading}
			onChange={(value) => props.onChange(options.find((supplier) => supplier.id === value) ?? null)}
			error={props.error}
			touched={!!props.error}
			required={props.required}
		/>
	);
}
