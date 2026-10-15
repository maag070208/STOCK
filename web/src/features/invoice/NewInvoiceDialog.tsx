import { ITDatePicker, ITInput, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
	hasRememberedWorkspace,
	invoiceApi,
	NEW_INVOICE_FIELDS,
	newInvoiceErrors,
	rememberWorkspace,
	sessionWorkspace,
	type Invoice,
	type NewInvoiceField,
	type Ref,
} from "@entities/invoice";
import { PartnerPicker } from "@entities/partner";
import { firstInvalidField } from "@shared/lib/focusTrap";
import { businessToday, isoDate } from "@shared/lib/format";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// The modal of a new invoice (F8-06): four fields, the keyboard only, and the last supplier and warehouse remembered for
// the session. It goes through FormDialog, so the role of a dialog, its focus trap and its submit are written in one
// place and not here (web.md section 8); the second action is the one that goes on to the materials.
export function NewInvoiceDialog(props: {
	isOpen: boolean;
	warehouses: Ref[];
	onClose: () => void;
	onCreated: (invoice: Invoice) => void;
}) {
	const { t } = useTranslation(["invoices", "common"]);
	const [number, setNumber] = useState("");
	const [supplierId, setSupplierId] = useState("");
	const [invoiceDate, setInvoiceDate] = useState<Date | null>(null);
	const [warehouseId, setWarehouseId] = useState("");
	const [prefilled, setPrefilled] = useState(false);
	const [tried, setTried] = useState(false);
	const submit = useSubmit({ unexpected: t("modal.network") });

	// Every opening starts from the last workspace of the session, today, and an empty number.
	useEffect(() => {
		if (!props.isOpen) return;
		const last = sessionWorkspace();
		setNumber("");
		setSupplierId(last.supplierId);
		setInvoiceDate(businessToday());
		setWarehouseId(last.warehouseId);
		setPrefilled(hasRememberedWorkspace(last));
		setTried(false);
		submit.reset();
		// The kit gives the panel the focus a moment after it opens; the number is asked for the focus until it has it.
		let tries = 0;
		const focus = window.setInterval(() => {
			const field = document.querySelector<HTMLElement>('[name="number"]');
			field?.focus();
			if ((field && document.activeElement === field) || ++tries > 20) window.clearInterval(focus);
		}, 60);
		return () => window.clearInterval(focus);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	// `newInvoiceErrors` answers the codes of the catalog; the field shows the message of the person's language.
	const errors = tried ? newInvoiceErrors({ number, supplierId, invoiceDate, warehouseId }) : {};
	const fieldError = (field: NewInvoiceField) => {
		const code = errors[field] ?? submit.fieldErrors[field];
		if (!code) return undefined;
		return code === "FIELD_REQUIRED" ? t("common:validation.required") : code;
	};

	async function save() {
		setTried(true);
		const missing = firstInvalidField(newInvoiceErrors({ number, supplierId, invoiceDate, warehouseId }), NEW_INVOICE_FIELDS);
		if (missing) {
			document.querySelector<HTMLElement>(`[name="${missing}"]`)?.focus();
			return;
		}
		const created = await submit.run(() =>
			invoiceApi.create({ number: number.trim().toUpperCase(), supplierId, invoiceDate: isoDate(invoiceDate!), warehouseId, lines: [] }),
		);
		if (!created) {
			// A refusal of a field takes the focus to it; anything else stays on the form with its message.
			const refused = firstInvalidField(submit.fieldErrors, NEW_INVOICE_FIELDS);
			if (refused) document.querySelector<HTMLElement>(`[name="${refused}"]`)?.focus();
			return;
		}
		rememberWorkspace({ supplierId, warehouseId });
		props.onCreated(created);
	}

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={t("modal.title")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting}
			error={submit.error}
			submitLabel={submit.submitting ? t("modal.saving") : t("modal.save")}
		>
			<ITStack spacing={4} className="sf:w-[30rem]">
				<ITInput
					name="number"
					label={t("form.number")}
					value={number}
					onChange={(event) => setNumber(String(event.target.value).toUpperCase())}
					error={fieldError("number")}
					touched={Boolean(fieldError("number"))}
					required
				/>
				<PartnerPicker
					name="supplierId"
					label={t("form.supplier")}
					value={supplierId}
					onChange={(supplier) => {
						const chosen = supplier?.id ?? "";
						setSupplierId(chosen);
						rememberWorkspace({ supplierId: chosen, warehouseId });
					}}
					error={fieldError("supplierId")}
					required
				/>
				<ITDatePicker
					name="invoiceDate"
					label={t("form.date")}
					value={invoiceDate ?? businessToday()}
					onChange={(event) => event.target.value instanceof Date && setInvoiceDate(event.target.value)}
					error={fieldError("invoiceDate")}
					touched={Boolean(fieldError("invoiceDate"))}
					required
				/>
				<ITSelect
					name="warehouseId"
					label={t("form.warehouse")}
					value={warehouseId}
					onChange={(event) => {
						setWarehouseId(event.target.value);
						rememberWorkspace({ supplierId, warehouseId: event.target.value });
					}}
					options={props.warehouses.map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code ?? ""} · ${warehouse.name ?? ""}` }))}
					valueField="value"
					labelField="label"
					error={fieldError("warehouseId")}
					touched={Boolean(fieldError("warehouseId"))}
					required
				/>
				{prefilled && <ITText muted>{t("modal.remembered")}</ITText>}
			</ITStack>
		</FormDialog>
	);
}
