import { ITSearchSelect, ITStack, ITText, ITTextarea } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { canAdjust, invoiceApi, type StaffMember } from "@entities/invoice";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

export function BulkAssignDialog(props: {
	invoiceId: string;
	isOpen: boolean;
	lineCount: number;
	staff: StaffMember[];
	onClose: () => void;
	onSaved: () => void;
}) {
	const { t } = useTranslation(["invoices", "common"]);
	const [operatorId, setOperatorId] = useState("");
	const submit = useSubmit();

	useEffect(() => {
		if (!props.isOpen) return;
		setOperatorId("");
		submit.reset();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	async function save() {
		if (!operatorId) return;
		const saved = await submit.run(() => invoiceApi.bulkAssign(props.invoiceId, operatorId));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={t("bulkDialog.title")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting || !operatorId}
			error={submit.error}
			submitLabel={t("bulkDialog.save")}
		>
			<ITStack spacing={4} className="sf:min-w-[28rem]">
				<ITText>{t("bulkDialog.lines", { count: props.lineCount })}</ITText>
				<ITSearchSelect
					name="bulk-operator"
					label={t("bulkDialog.operator")}
					value={operatorId}
					onChange={(value) => setOperatorId(String(value))}
					options={props.staff.map((member) => ({ value: member.id, label: member.name }))}
					valueField="value"
					labelField="label"
					required
				/>
			</ITStack>
		</FormDialog>
	);
}

export function CancelInvoiceDialog(props: { invoiceId: string; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [note, setNote] = useState("");
	const submit = useSubmit();

	useEffect(() => {
		if (!props.isOpen) return;
		setNote("");
		submit.reset();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	async function save() {
		if (!canAdjust(note)) return;
		const saved = await submit.run(() => invoiceApi.cancel(props.invoiceId, note));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={t("cancellation.title")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting || !canAdjust(note)}
			error={submit.error}
			submitLabel={t("cancellation.confirm")}
		>
			<ITStack className="sf:min-w-[28rem]">
				<ITTextarea name="note" label={t("cancellation.note")} value={note} onChange={setNote} placeholder={t("cancellation.noteHint")} rows={3} maxLength={500} />
			</ITStack>
		</FormDialog>
	);
}
