import { ITInput } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { warehouseApi, type Warehouse } from "@entities/warehouse";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// A warehouse: its code (fixed once created) and its name. A new warehouse gets its temporary location from the API.
export function WarehouseDialog(props: { warehouse: Warehouse | null; isOpen: boolean; onClose: () => void; onSaved: (warehouse: Warehouse) => void }) {
	const { t } = useTranslation(["warehouses", "common"]);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen) {
			setCode(props.warehouse?.code ?? "");
			setName(props.warehouse?.name ?? "");
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.warehouse]);

	const required = t("common:validation.required");
	const errors = {
		code: submit.fieldErrors.code ?? (tried && !props.warehouse && !code.trim() ? required : undefined),
		name: submit.fieldErrors.name ?? (tried && !name.trim() ? required : undefined),
	};

	async function save() {
		setTried(true);
		if ((!props.warehouse && !code.trim()) || !name.trim()) return;
		const saved = await submit.run(() => (props.warehouse ? warehouseApi.update(props.warehouse.id, { name }) : warehouseApi.create({ code, name })));
		if (saved) props.onSaved(saved);
	}

	return (
		<FormDialog isOpen={props.isOpen} title={props.warehouse ? t("edit") : t("new")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<ITInput
				name="code"
				label={t("form.code")}
				value={code}
				disabled={!!props.warehouse}
				onChange={(e) => setCode(e.target.value.toUpperCase())}
				error={errors.code ?? (props.warehouse ? undefined : t("form.codeHint"))}
				touched={!!errors.code}
				required
			/>
			<ITInput name="name" label={t("form.name")} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} touched={!!errors.name} required />
		</FormDialog>
	);
}
