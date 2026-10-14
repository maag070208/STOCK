import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { productApi } from "../api/productApi";
import type { Product } from "../model/types";

// Picks an active material by its code or name, searching the API as the person types.
export function ProductPicker(props: { name: string; label: string; value: string; onChange: (product: Product | null) => void; error?: string; required?: boolean }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [options, setOptions] = useState<Product[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	async function search(query: string) {
		const request = ++latest.current;
		setLoading(true);
		try {
			const filters = { active: "true", ...(query.trim() ? { name: query.trim() } : {}) };
			const [byName, byCode] = await Promise.all([
				productApi.query({ page: 1, limit: 20, filters }),
				query.trim() ? productApi.query({ page: 1, limit: 20, filters: { active: "true", code: query.trim() } }) : Promise.resolve({ data: [], total: 0 }),
			]);
			// Only the answer to the last search is shown.
			if (request === latest.current) setOptions([...byCode.data, ...byName.data.filter((p) => !byCode.data.some((c) => c.id === p.id))]);
		} finally {
			if (request === latest.current) setLoading(false);
		}
	}

	useEffect(() => {
		void search("");
	}, []);

	return (
		<ITSearchSelect
			name={props.name}
			label={props.label}
			placeholder={t("form.productSearch")}
			value={props.value}
			options={options.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))}
			onSearch={(query) => void search(query)}
			isLoading={loading}
			onChange={(value) => props.onChange(options.find((p) => p.id === value) ?? null)}
			error={props.error}
			touched={!!props.error}
			required={props.required}
		/>
	);
}
