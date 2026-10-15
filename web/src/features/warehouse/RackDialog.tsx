import { ITAlert, ITGrid, ITInput, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { existingCodes, locationApi, rackCodes, rackErrors, type Location, type RackValues, type Warehouse } from "@entities/warehouse";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

const EMPTY: RackValues = { aisle: "", rack: "", levels: "" };

// The rack form (locations.md section 2): a whole rack at once, one location per level.
// It shows the codes it will create before it is sent. If any of them exists, the API creates nothing and names them.
export function RackDialog(props: { warehouse: Warehouse; isOpen: boolean; onClose: () => void; onCreated: (locations: Location[]) => void }) {
	const { t } = useTranslation(["warehouses", "common"]);
	const [values, setValues] = useState<RackValues>(EMPTY);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();
	const set = (field: keyof RackValues) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	useEffect(() => {
		if (props.isOpen) {
			setValues(EMPTY);
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	const codes = rackCodes(values);
	const local = rackErrors(values);
	const hints = { aisle: t("rack.aisleHint"), rack: t("rack.rackHint"), levels: t("rack.levelsHint") };
	// The API's message wins; before sending, a missing field or one outside its rule shows its hint.
	const error = (field: keyof RackValues) =>
		submit.fieldErrors[field] ?? (tried && local[field] ? (local[field] === "FIELD_REQUIRED" ? t("common:validation.required") : hints[field]) : undefined);
	const existing = existingCodes(submit.fieldErrors);

	async function save() {
		setTried(true);
		if (codes.length === 0) return;
		const created = await submit.run(() =>
			locationApi.createRack({ warehouseId: props.warehouse.id, aisle: values.aisle.trim().toUpperCase(), rack: values.rack.trim(), levels: Number(values.levels) }),
		);
		if (created) props.onCreated(created.locations);
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("rack.new")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("common:actions.create")}>
			<ITInput name="warehouse" label={t("rack.warehouse")} value={`${props.warehouse.code} · ${props.warehouse.name}`} onChange={() => undefined} disabled />
			<ITGrid container spacing={3}>
				{(["aisle", "rack", "levels"] as const).map((field) => (
					<ITGrid key={field} item xs={12} sm={4}>
						<ITInput
							name={field}
							label={t(`rack.${field}`)}
							value={values[field]}
							maxLength={field === "aisle" ? 1 : 2}
							onChange={(e) => set(field)(field === "aisle" ? e.target.value.toUpperCase() : e.target.value)}
							error={error(field) ?? hints[field]}
							touched={!!error(field)}
							required
						/>
					</ITGrid>
				))}
			</ITGrid>
			<ITText data-testid="rack-preview" aria-live="polite">
				{codes.length > 0 ? t("rack.preview", { codes: codes.join(", ") }) : t("rack.previewEmpty")}
			</ITText>
			{existing.length > 0 && <ITAlert variant="error">{t("rack.exists", { codes: existing.join(", ") })}</ITAlert>}
		</FormDialog>
	);
}
