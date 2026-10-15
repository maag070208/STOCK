import { ITCheckbox, ITInput, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { WarehousePicker } from "@entities/warehouse";
import { isIpAddress, printerApi, type Printer } from "@entities/label";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

export interface WarehouseOption {
	id: string;
	code: string;
	name: string;
}

// A printer: its name, its warehouse and its address. The port is always 9100, so it is shown, not asked for.
export function PrinterDialog(props: { printer: Printer | null; warehouses: WarehouseOption[]; isOpen: boolean; onClose: () => void; onSaved: (printer: Printer) => void }) {
	const { t } = useTranslation(["labels", "common"]);
	const [name, setName] = useState("");
	const [warehouseId, setWarehouseId] = useState("");
	const [ipAddress, setIpAddress] = useState("");
	const [active, setActive] = useState(true);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen) {
			setName(props.printer?.name ?? "");
			setWarehouseId(props.printer?.warehouseId ?? "");
			setIpAddress(props.printer?.ipAddress ?? "");
			setActive(props.printer?.active ?? true);
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.printer]);

	const required = t("common:validation.required");
	const local = {
		name: !name.trim() ? required : undefined,
		warehouseId: !warehouseId ? required : undefined,
		ipAddress: !ipAddress.trim() ? required : !isIpAddress(ipAddress) ? t("printers.form.ipInvalid") : undefined,
	};
	const errors = {
		name: submit.fieldErrors.name ?? (tried ? local.name : undefined),
		warehouseId: submit.fieldErrors.warehouseId ?? (tried ? local.warehouseId : undefined),
		ipAddress: submit.fieldErrors.ipAddress ?? (tried ? local.ipAddress : undefined),
	};

	async function save() {
		setTried(true);
		if (local.name || local.warehouseId || local.ipAddress) return;
		const input = { name: name.trim(), warehouseId, ipAddress: ipAddress.trim() };
		const saved = await submit.run(() => (props.printer ? printerApi.update(props.printer.id, { ...input, active }) : printerApi.create(input)));
		if (saved) props.onSaved(saved);
	}

	return (
		<FormDialog isOpen={props.isOpen} title={props.printer ? t("printers.edit") : t("printers.new")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<ITInput name="name" label={t("printers.form.name")} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} touched={!!errors.name} required />
			<WarehousePicker
				name="warehouseId"
				label={t("printers.form.warehouse")}
				value={warehouseId}
				onChange={(chosen) => setWarehouseId(chosen)}
				error={errors.warehouseId}
				required
			/>
			<ITInput
				name="ipAddress"
				label={t("printers.form.ipAddress")}
				value={ipAddress}
				onChange={(e) => setIpAddress(e.target.value)}
				error={errors.ipAddress ?? t("printers.form.ipHint")}
				touched={!!errors.ipAddress}
				required
			/>
			<ITText className="sf:text-body-sm" muted>
				{t("printers.form.port")}
			</ITText>
			{props.printer && <ITCheckbox name="active" label={t("printers.form.active")} checked={active} onChange={setActive} />}
		</FormDialog>
	);
}
