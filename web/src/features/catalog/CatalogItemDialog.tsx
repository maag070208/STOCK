import { ITInput } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { catalogApi, type CatalogItem, type CatalogLabel } from "@entities/catalog";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

interface Values {
	code: string;
	labelEs: string;
	labelEn: string;
	descriptionEs: string;
	descriptionEn: string;
	sortOrder: string;
	active: boolean;
	terminal: boolean;
	decimalPlaces: string;
}

const fromItem = (item: CatalogItem | null): Values => ({
	code: item?.code ?? "",
	labelEs: item?.labels.es?.label ?? "",
	labelEn: item?.labels.en?.label ?? "",
	descriptionEs: item?.labels.es?.description ?? "",
	descriptionEn: item?.labels.en?.description ?? "",
	sortOrder: item ? String(item.sortOrder) : "",
	active: item?.active ?? true,
	terminal: item?.attributes.terminal === true,
	decimalPlaces: item?.attributes.decimal_places !== undefined ? String(item.attributes.decimal_places) : "",
});

// A value of a catalog: its code (fixed once created), its labels in Spanish and English, its order, and its attributes.
export function CatalogItemDialog(props: { catalog: string; item: CatalogItem | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["catalogs", "common"]);
	const [values, setValues] = useState<Values>(fromItem(null));
	const submit = useSubmit();
	const set = (field: keyof Values) => (value: string | boolean) => setValues((current) => ({ ...current, [field]: value }));

	useEffect(() => {
		if (props.isOpen) {
			setValues(fromItem(props.item));
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.item]);

	async function save() {
		// A language without a name is left out: the API then asks for it if the value is active.
		const labels: Record<string, CatalogLabel> = {};
		if (values.labelEs) labels.es = { label: values.labelEs, description: values.descriptionEs || null };
		if (values.labelEn) labels.en = { label: values.labelEn, description: values.descriptionEn || null };
		const attributes: Record<string, unknown> = {};
		if (values.terminal) attributes.terminal = true;
		if (values.decimalPlaces !== "") attributes.decimal_places = Number(values.decimalPlaces);
		const changes = { labels, active: values.active, attributes, sortOrder: values.sortOrder === "" ? undefined : Number(values.sortOrder) };
		const saved = await submit.run(() =>
			props.item ? catalogApi.updateItem(props.catalog, props.item.code, changes) : catalogApi.createItem(props.catalog, { code: values.code, ...changes }),
		);
		if (saved) props.onSaved();
	}

	const errors = submit.fieldErrors;
	return (
		<FormDialog isOpen={props.isOpen} title={props.item ? t("editItem") : t("newItem")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<div className="sf:grid sf:min-w-[28rem] sf:grid-cols-2 sf:gap-3">
				<div className="sf:col-span-2">
					<ITInput name="code" label={t("form.code")} value={values.code} disabled={!!props.item} onChange={(e) => set("code")(e.target.value.toUpperCase())} error={errors.code ?? (props.item ? undefined : t("form.codeHint"))} touched={!!errors.code} required />
				</div>
				<ITInput name="labelEs" label={t("form.labelEs")} value={values.labelEs} onChange={(e) => set("labelEs")(e.target.value)} error={errors["labels.es"] ?? errors["labels.es.label"]} touched={!!(errors["labels.es"] ?? errors["labels.es.label"])} />
				<ITInput name="labelEn" label={t("form.labelEn")} value={values.labelEn} onChange={(e) => set("labelEn")(e.target.value)} error={errors["labels.en"] ?? errors["labels.en.label"]} touched={!!(errors["labels.en"] ?? errors["labels.en.label"])} />
				<ITInput name="descriptionEs" label={t("form.descriptionEs")} value={values.descriptionEs} onChange={(e) => set("descriptionEs")(e.target.value)} />
				<ITInput name="descriptionEn" label={t("form.descriptionEn")} value={values.descriptionEn} onChange={(e) => set("descriptionEn")(e.target.value)} />
				<ITInput name="sortOrder" type="number" label={t("form.sortOrder")} value={values.sortOrder} onChange={(e) => set("sortOrder")(e.target.value)} error={errors.sortOrder} touched={!!errors.sortOrder} />
				<ITInput name="decimalPlaces" type="number" label={t("form.decimalPlaces")} value={values.decimalPlaces} onChange={(e) => set("decimalPlaces")(e.target.value)} error={errors["attributes.decimal_places"]} touched={!!errors["attributes.decimal_places"]} />
				<label className="sf:flex sf:items-center sf:gap-2 sf:text-body-md">
					<input type="checkbox" name="terminal" checked={values.terminal} onChange={(e) => set("terminal")(e.target.checked)} />
					{t("form.terminal")}
				</label>
				<label className="sf:flex sf:items-center sf:gap-2 sf:text-body-md">
					<input type="checkbox" name="active" checked={values.active} onChange={(e) => set("active")(e.target.checked)} />
					{t("form.active")}
				</label>
			</div>
		</FormDialog>
	);
}
