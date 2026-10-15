import { ITButton, ITDatePicker, ITDialog, ITInput, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { FaTimes } from "react-icons/fa";
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
import { SupplierPicker } from "@entities/partner";
import { firstInvalidField, focusTrapIndex } from "@shared/lib/focusTrap";
import { businessToday, isoDate } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";

// The modal of a new invoice (F8-06): four fields, the keyboard only, and the last supplier and warehouse remembered for
// the session. It does not close while it is saving, Enter sends the action that goes on to the materials, and the focus
// stays inside while it is open and goes back to whoever opened it (F8-08).
export function NewInvoiceDialog(props: {
	isOpen: boolean;
	warehouses: Ref[];
	onClose: () => void;
	onCreated: (invoice: Invoice, action: "save" | "materials") => void;
}) {
	const { t } = useTranslation(["invoices", "common"]);
	const titleId = useId();
	const form = useRef<HTMLFormElement>(null);
	const opener = useRef<Element | null>(null);
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
		opener.current = document.activeElement;
		// The kit gives the panel the focus a moment after it opens; the number is asked for the focus until it has it.
		let tries = 0;
		const focus = window.setInterval(() => {
			const field = form.current?.querySelector<HTMLElement>('[name="number"]');
			field?.focus();
			if ((field && document.activeElement === field) || ++tries > 20) window.clearInterval(focus);
		}, 60);
		return () => window.clearInterval(focus);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	// Closing returns the focus to whoever opened the modal, so the person keeps working where they were.
	const close = () => {
		if (submit.submitting) return;
		props.onClose();
		if (opener.current instanceof HTMLElement) opener.current.focus();
	};

	// `newInvoiceErrors` answers the codes of the catalog; the field shows the message of the person's language.
	const errors = tried ? newInvoiceErrors({ number, supplierId, invoiceDate, warehouseId }) : {};
	const fieldError = (field: NewInvoiceField) => {
		const code = errors[field] ?? submit.fieldErrors[field];
		if (!code) return undefined;
		return code === "FIELD_REQUIRED" ? t("common:validation.required") : code;
	};

	// The two buttons: the primary one goes on to the materials, the secondary one back to the list (F8-06).
	async function save(action: "save" | "materials") {
		setTried(true);
		const missing = firstInvalidField(newInvoiceErrors({ number, supplierId, invoiceDate, warehouseId }), NEW_INVOICE_FIELDS);
		if (missing) {
			form.current?.querySelector<HTMLElement>(`[name="${missing}"]`)?.focus();
			return;
		}
		const created = await submit.run(() =>
			invoiceApi.create({ number: number.trim().toUpperCase(), supplierId, invoiceDate: isoDate(invoiceDate!), warehouseId, lines: [] }),
		);
		if (!created) {
			// A refusal of a field takes the focus to it; anything else stays on the form with its message.
			const refused = firstInvalidField(submit.fieldErrors, NEW_INVOICE_FIELDS);
			if (refused) form.current?.querySelector<HTMLElement>(`[name="${refused}"]`)?.focus();
			return;
		}
		rememberWorkspace({ supplierId, warehouseId });
		props.onCreated(created, action);
	}

	// Escape closes, Enter sends the primary action, and Tab wraps inside the modal.
	function onKeyDown(event: KeyboardEvent<HTMLFormElement>) {
		if (event.key === "Escape") {
			event.stopPropagation();
			close();
			return;
		}
		if (event.key !== "Tab") return;
		const focusable = [...(form.current?.querySelectorAll<HTMLElement>("input, select, textarea, button, [href], [tabindex]:not([tabindex='-1'])") ?? [])].filter(
			(element) => !element.hasAttribute("disabled") && element.offsetParent !== null,
		);
		const next = focusTrapIndex(focusable.length, focusable.indexOf(document.activeElement as HTMLElement), event.shiftKey ? -1 : 1);
		if (next === null) return;
		event.preventDefault();
		focusable[next]?.focus();
	}

	if (!props.isOpen) return null;

	return (
		<ITDialog isOpen onClose={close}>
			<form
				ref={form}
				role="dialog"
				aria-modal="true"
				aria-labelledby={titleId}
				onKeyDown={onKeyDown}
				onSubmit={(event) => {
					event.preventDefault();
					void save("materials");
				}}
				noValidate
				className="sf:flex sf:w-[30rem] sf:flex-col sf:gap-4"
				data-testid="new-invoice"
			>
				<div className="sf:flex sf:items-center sf:justify-between sf:gap-2">
					<h2 id={titleId} className="sf:text-title-md">
						{t("modal.title")}
					</h2>
					<button type="button" aria-label={t("modal.close")} onClick={close} className="sf:rounded-md sf:p-2 sf:text-on-surface-muted">
						<FaTimes aria-hidden />
					</button>
				</div>

				<ITInput
					name="number"
					label={t("form.number")}
					value={number}
					onChange={(event) => setNumber(String(event.target.value).toUpperCase())}
					error={fieldError("number")}
					touched={Boolean(fieldError("number"))}
					required
				/>
				<SupplierPicker
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
				{prefilled && <p className="sf:text-body-md sf:text-on-surface-muted">{t("modal.remembered")}</p>}

				{submit.error && <p className="sf:text-body-md sf:text-danger">{submit.error}</p>}

				<div className="sf:flex sf:flex-wrap sf:justify-end sf:gap-2">
					<ITButton variant="outlined" label={t("common:actions.cancel")} onClick={close} />
					<ITButton variant="outlined" label={t("modal.save")} onClick={() => void save("save")} disabled={submit.submitting} />
					<ITButton type="submit" label={submit.submitting ? t("modal.saving") : t("modal.saveAndAdd")} disabled={submit.submitting} />
				</div>
			</form>
		</ITDialog>
	);
}
