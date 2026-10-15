import { ITGrid, ITInput, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { CatalogItem } from "@entities/catalog";
import { productApi, type Product } from "@entities/product";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

interface Values {
	code: string;
	name: string;
	unit: string;
	barcode: string;
}

const fromProduct = (product: Product | null, units: CatalogItem[]): Values => ({
	code: product?.code ?? "",
	name: product?.name ?? "",
	unit: product?.unit.code ?? units[0]?.code ?? "",
	// The barcode shows only when it is not the code: empty means "use the code".
	barcode: product && product.barcode !== product.code ? product.barcode : "",
});

// A material: its code (fixed once created), name, unit, and barcode. The API normalizes the name and the barcode.
export function ProductDialog(props: { product: Product | null; units: CatalogItem[]; isOpen: boolean; onClose: () => void; onSaved: (product: Product) => void }) {
	const { t } = useTranslation(["products", "common"]);
	const [values, setValues] = useState<Values>(fromProduct(null, props.units));
	const [tried, setTried] = useState(false);
	const submit = useSubmit();
	const set = (field: keyof Values) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	useEffect(() => {
		if (props.isOpen) {
			setValues(fromProduct(props.product, props.units));
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.product]);

	const required = t("common:validation.required");
	const missing = (field: keyof Values) => tried && !values[field].trim() && (field !== "code" || !props.product) ? required : undefined;
	const error = (field: keyof Values) => submit.fieldErrors[field] ?? missing(field);

	async function save() {
		setTried(true);
		if ((!props.product && !values.code.trim()) || !values.name.trim() || !values.unit) return;
		const changes = { name: values.name, unit: values.unit, barcode: values.barcode };
		const saved = await submit.run(() => (props.product ? productApi.update(props.product.id, changes) : productApi.create({ code: values.code, ...changes })));
		if (saved) props.onSaved(saved);
	}

	return (
		<FormDialog isOpen={props.isOpen} title={props.product ? t("edit") : t("new")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<ITGrid container spacing={3} className="sf:min-w-[28rem]">
				<ITGrid item xs={12} sm={6}>
					<ITInput
						name="code"
					label={t("form.code")}
					value={values.code}
					disabled={!!props.product}
					onChange={(e) => set("code")(e.target.value.toUpperCase())}
						error={error("code") ?? (props.product ? undefined : t("form.codeHint"))}
						touched={!!error("code")}
						required
					/>
				</ITGrid>
				<ITGrid item xs={12} sm={6}>
					<ITSelect
						name="unit"
					label={t("form.unit")}
					value={values.unit}
					onChange={(e) => set("unit")(e.target.value)}
						options={props.units.map((unit) => ({ value: unit.code, label: unit.label ?? unit.code }))}
						valueField="value"
						labelField="label"
						error={error("unit")}
						touched={!!error("unit")}
						required
					/>
				</ITGrid>
				<ITGrid item xs={12}>
					<ITInput name="name" label={t("form.name")} value={values.name} onChange={(e) => set("name")(e.target.value)} error={error("name")} touched={!!error("name")} required />
				</ITGrid>
				<ITGrid item xs={12}>
					<ITInput
						name="barcode"
						label={t("form.barcode")}
						value={values.barcode}
						onChange={(e) => set("barcode")(e.target.value.toUpperCase())}
						error={error("barcode") ?? t("form.barcodeHint")}
						touched={!!error("barcode")}
					/>
				</ITGrid>
			</ITGrid>
		</FormDialog>
	);
}
