import { ITSearchSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { productApi } from "../api/productApi";
import type { Product } from "../model/types";

// A scanner and a manual search share the API lookup: barcode, then code, then name.
export function ProductPicker(props: { name: string; label: string; value: string; onChange: (product: Product | null) => void; error?: string; required?: boolean }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [options, setOptions] = useState<Product[]>([]);
	const [loading, setLoading] = useState(false);
	const latest = useRef(0);

	async function search(query: string) {
		const request = ++latest.current;
		setLoading(true);
		try {
			const clean = query.trim();
			const found = clean
				? await productApi.lookup(clean).catch(() => ({ match: "NAME" as const, products: [] }))
				: await productApi.query({ page: 1, limit: 20, filters: { active: "true" } }).then((page) => ({ match: "NAME" as const, products: page.data }));
			if (request === latest.current) setOptions(found.products);
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
