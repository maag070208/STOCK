import { ITStack, ITText, ITTextarea } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { canAdjust, invoiceApi, receivingApi, sharesError, type InvoiceLine, type ShareInput, type StaffMember } from "@entities/invoice";
import { formatQuantity } from "@shared/lib/format";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { SharesEditor } from "./SharesEditor";

// Path A: the shares of a saved line. Save stays closed until they add up to the line.
export function AssignDialog(props: { invoiceId: string; line: InvoiceLine | null; staff: StaffMember[]; onClose: () => void; onSaved: () => void }) {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const [shares, setShares] = useState<ShareInput[]>([]);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();
	const line = props.line;

	useEffect(() => {
		if (line) {
			setShares(line.assignments.length ? line.assignments.map((a) => ({ operatorId: a.operator.id, quantity: String(a.expectedQuantity) })) : [{ operatorId: "", quantity: String(line.quantity) }]);
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [line]);

	if (!line) return null;
	const blocked = sharesError(String(line.quantity), shares) !== null;

	async function save() {
		setTried(true);
		if (!line || blocked) return;
		const saved = await submit.run(() => invoiceApi.setAssignments(props.invoiceId, line.id, shares.map((s) => ({ operatorId: s.operatorId, quantity: Number(s.quantity) }))));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen title={t("assignDialog.title", { product: line.product.name ?? "" })} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting || (tried && blocked)} error={submit.error}>
			<ITText>{t("assignDialog.line", { quantity: formatQuantity(line.quantity, line.unit?.decimalPlaces, i18n.language) })}</ITText>
			<ITStack className="sf:min-w-[30rem]">
				<SharesEditor line={String(line.quantity)} decimals={line.unit?.decimalPlaces ?? null} staff={props.staff} shares={shares} onChange={setShares} showErrors={tried} />
			</ITStack>
		</FormDialog>
	);
}

// The adjustment of a discrepancy (material-receiving.md section 5): it needs a note before it can be sent.
export function AdjustDialog(props: { assignmentId: string | null; summary: { expected: string; counted: string; difference: string } | null; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [note, setNote] = useState("");
	const submit = useSubmit();

	useEffect(() => {
		if (props.assignmentId) {
			setNote("");
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.assignmentId]);

	async function save() {
		if (!props.assignmentId || !canAdjust(note)) return;
		const saved = await submit.run(() => receivingApi.adjust(props.assignmentId!, note));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog
			isOpen={!!props.assignmentId}
			title={t("adjustDialog.title")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting || !canAdjust(note)}
			error={submit.error}
			submitLabel={t("adjustDialog.confirm")}
		>
			{props.summary && <ITText>{t("adjustDialog.body", props.summary)}</ITText>}
			<ITStack className="sf:min-w-[28rem]">
				<ITTextarea name="note" label={t("adjustDialog.note")} value={note} onChange={setNote} placeholder={t("adjustDialog.noteHint")} rows={3} maxLength={500} />
			</ITStack>
		</FormDialog>
	);
}
