import { ITInput, ITSelect, ITTextarea } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { orderApi, type Order, type OrderLine, type OrderOperator } from "@entities/order";
import { locationApi, type Location } from "@entities/warehouse";
import { formatQuantity } from "@shared/lib/format";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// The actions an order takes (order-fulfillment.md sections 6, 7a and 8). Each dialog sends one of them, needs what it
// needs (a note, an operator, a location), and hands the answer back to the detail, which refreshes.

// The assigned operator of an order (section 7a): the operators of its warehouse, in the staff template.
export function AssignOrderDialog(props: { order: Order | null; isOpen: boolean; title: string; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
	const [operators, setOperators] = useState<OrderOperator[]>([]);
	const [operatorId, setOperatorId] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen && props.order) {
			setOperatorId(props.order.operator?.id ?? "");
			setTried(false);
			submit.reset();
			void orderApi.operators(props.order.warehouse.id).then(setOperators).catch(() => setOperators([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order]);

	async function save() {
		setTried(true);
		if (!props.order || !operatorId) return;
		const saved = await submit.run(() => orderApi.assign(props.order!.id, operatorId));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={props.title}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting}
			error={submit.error}
		>
			<div className="sf:min-w-[24rem]">
				<ITSelect
					name="operatorId"
					label={t("assignDialog.operator")}
					value={operatorId}
					onChange={(e) => setOperatorId(e.target.value)}
					options={operators.map((operator) => ({ value: operator.id, label: operator.name }))}
					valueField="value"
					labelField="label"
					error={submit.fieldErrors.operatorId ?? (tried && !operatorId ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.operatorId}
					required
				/>
			</div>
			{operators.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("assignDialog.noStaff")}</p>}
		</FormDialog>
	);
}

// Changing the operator of an open order with a note (section 8, action 1).
export function ReassignOperatorDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
	const [operators, setOperators] = useState<OrderOperator[]>([]);
	const [operatorId, setOperatorId] = useState("");
	const [note, setNote] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen && props.order) {
			setOperatorId("");
			setNote("");
			setTried(false);
			submit.reset();
			void orderApi.operators(props.order.warehouse.id).then(setOperators).catch(() => setOperators([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order]);

	async function save() {
		setTried(true);
		if (!props.order || !operatorId || !note.trim()) return;
		const saved = await submit.run(() => orderApi.reassignOperator(props.order!.id, operatorId, note.trim()));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("reassignDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<p className="sf:text-body-md">{t("reassignDialog.body")}</p>
			<div className="sf:grid sf:min-w-[26rem] sf:grid-cols-1 sf:gap-3">
				<ITSelect
					name="operatorId"
					label={t("reassignDialog.operator")}
					value={operatorId}
					onChange={(e) => setOperatorId(e.target.value)}
					options={operators.map((operator) => ({ value: operator.id, label: operator.name }))}
					valueField="value"
					labelField="label"
					error={submit.fieldErrors.operatorId ?? (tried && !operatorId ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.operatorId}
					required
				/>
				<ITTextarea
					name="note"
					label={t("reassignDialog.note")}
					value={note}
					onChange={setNote}
					placeholder={t("reassignDialog.noteHint")}
					rows={3}
					maxLength={500}
					error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
				/>
			</div>
		</FormDialog>
	);
}

// Validating a fulfilled order (section 6). The note is optional: it is required only when something does not match, and
// then the API sends the order back to FULFILLING.
export function ValidateOrderDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
	const [note, setNote] = useState("");
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen) {
			setNote("");
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	async function save() {
		if (!props.order) return;
		const saved = await submit.run(() => orderApi.validate(props.order!.id, note));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("validateDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("validateDialog.confirm")}>
			<p className="sf:text-body-md">{t("validateDialog.body")}</p>
			<div className="sf:min-w-[28rem]">
				<ITTextarea name="note" label={t("validateDialog.note")} value={note} onChange={setNote} placeholder={t("validateDialog.noteHint")} rows={3} maxLength={500} />
			</div>
		</FormDialog>
	);
}

// Accepting a shortage the customer accepts (section 10): only the manager or the administrator, and always with a note.
export function AcceptDifferenceDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
	const [note, setNote] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen) {
			setNote("");
			setTried(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	async function save() {
		setTried(true);
		if (!props.order || !note.trim()) return;
		const saved = await submit.run(() => orderApi.acceptDifference(props.order!.id, note.trim()));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("differenceDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("differenceDialog.confirm")}>
			<p className="sf:text-body-md">{t("differenceDialog.body")}</p>
			<div className="sf:min-w-[28rem]">
				<ITTextarea
					name="note"
					label={t("differenceDialog.note")}
					value={note}
					onChange={setNote}
					placeholder={t("differenceDialog.noteHint")}
					rows={3}
					maxLength={500}
					error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
				/>
			</div>
		</FormDialog>
	);
}

// Cancelling an order with nothing picked (section 8): it releases the reservation.
export function CancelOrderDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
	const submit = useSubmit();

	useEffect(() => {
		if (props.isOpen) submit.reset();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen]);

	async function save() {
		if (!props.order) return;
		const saved = await submit.run(() => orderApi.cancel(props.order!.id));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("cancelDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("cancelDialog.confirm")}>
			<p className="sf:text-body-md">{t("cancelDialog.body")}</p>
		</FormDialog>
	);
}

// Moving what was picked to another open order of the same warehouse (section 8, action 2). No balance changes: the
// goods already left the shelves and now serve the target order.
export function TransferPicksDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const [orders, setOrders] = useState<Order[]>([]);
	const [lineId, setLineId] = useState("");
	const [targetId, setTargetId] = useState("");
	const [remaining, setRemaining] = useState<number | null>(null);
	const [quantity, setQuantity] = useState("");
	const [note, setNote] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	const lines = (props.order?.lines ?? []).filter((line) => Number(line.pickedQuantity) > 0);
	const line = lines.find((candidate) => candidate.id === lineId) ?? null;

	useEffect(() => {
		if (props.isOpen && props.order) {
			setOrders([]);
			setLineId("");
			setTargetId("");
			setRemaining(null);
			setQuantity("");
			setNote("");
			setTried(false);
			submit.reset();
			void orderApi.openOf(props.order.warehouse.id).then((page) => setOrders(page.data.filter((order) => order.id !== props.order!.id))).catch(() => setOrders([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order]);

	// Once a target is chosen, its own line of the same material says how much it still needs.
	useEffect(() => {
		setRemaining(null);
		if (!targetId || !line) return;
		let live = true;
		void orderApi
			.get(targetId)
			.then((found) => {
				const target = (found.lines ?? []).find((candidate) => candidate.product.id === line.product.id);
				if (live) setRemaining(target ? Number(target.quantity) - Number(target.pickedQuantity) : 0);
			})
			.catch(() => undefined);
		return () => {
			live = false;
		};
	}, [targetId, line]);

	const quantityNumber = Number(quantity);
	const overPicked = line ? quantityNumber > Number(line.pickedQuantity) : false;
	const overTarget = remaining !== null && quantityNumber > remaining;
	const blocked = !line || quantityNumber <= 0 || overPicked || overTarget;
	const quantityError =
		tried && line ? (overTarget ? t("transferDialog.aboveTarget") : overPicked || quantityNumber <= 0 ? t("transferDialog.abovePicked") : undefined) : undefined;

	async function save() {
		setTried(true);
		if (!props.order || !line || !targetId || blocked || !note.trim()) return;
		const saved = await submit.run(() => orderApi.transfer(props.order!.id, { lineId: line.id, targetOrderId: targetId, quantity: quantityNumber, note: note.trim() }));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("transferDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("transferDialog.confirm")}>
			<p className="sf:text-body-md">{t("transferDialog.body")}</p>
			<div className="sf:grid sf:min-w-[32rem] sf:grid-cols-1 sf:gap-3">
				<ITSelect
					name="lineId"
					label={t("transferDialog.line")}
					value={lineId}
					onChange={(e) => setLineId(e.target.value)}
					options={lines.map((candidate) => ({ value: candidate.id, label: `${candidate.product.code} · ${candidate.product.name} (${t("detail.picked")} ${formatQuantity(candidate.pickedQuantity, candidate.unit?.decimalPlaces, i18n.language)})` }))}
					valueField="value"
					labelField="label"
					error={tried && !lineId ? t("common:validation.required") : undefined}
					touched={tried}
					required
				/>
				<ITSelect
					name="targetOrderId"
					label={t("transferDialog.target")}
					value={targetId}
					onChange={(e) => setTargetId(e.target.value)}
					options={orders.map((target) => ({ value: target.id, label: `${target.number} · ${target.customer.name ?? ""}` }))}
					valueField="value"
					labelField="label"
					error={tried && !targetId ? t("common:validation.required") : undefined}
					touched={tried}
					required
				/>
				{orders.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("transferDialog.noTargets")}</p>}
				<ITInput
					name="quantity"
					type="number"
					label={line ? `${t("transferDialog.quantity")} / ${formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces, i18n.language)}` : t("transferDialog.quantity")}
					value={quantity}
					onChange={(e) => setQuantity(e.target.value)}
					error={submit.fieldErrors.quantity ?? quantityError ?? (tried && !quantity.trim() ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.quantity}
					required
				/>
				<ITTextarea
					name="note"
					label={t("transferDialog.note")}
					value={note}
					onChange={setNote}
					placeholder={t("transferDialog.noteHint")}
					rows={3}
					maxLength={500}
					error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
				/>
			</div>
		</FormDialog>
	);
}

// Returning what was picked to a location of the warehouse (section 8, action 3): the only reassignment that changes a
// balance, with a movement in.
export function ReturnPicksDialog(props: { order: Order | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const [locations, setLocations] = useState<Location[]>([]);
	const [lineId, setLineId] = useState("");
	const [locationId, setLocationId] = useState("");
	const [quantity, setQuantity] = useState("");
	const [note, setNote] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	const lines = (props.order?.lines ?? []).filter((line) => Number(line.pickedQuantity) > 0);
	const line: OrderLine | null = lines.find((candidate) => candidate.id === lineId) ?? null;

	useEffect(() => {
		if (props.isOpen && props.order) {
			setLocations([]);
			setLineId("");
			setLocationId("");
			setQuantity("");
			setNote("");
			setTried(false);
			submit.reset();
			void locationApi
				.query({ page: 1, limit: 200, filters: { warehouseId: props.order.warehouse.id, active: "true" } })
				.then((page) => setLocations(page.data))
				.catch(() => setLocations([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order]);

	const quantityNumber = Number(quantity);
	const blocked = !line || quantityNumber <= 0 || quantityNumber > Number(line.pickedQuantity);

	async function save() {
		setTried(true);
		if (!props.order || !line || !locationId || blocked || !note.trim()) return;
		const saved = await submit.run(() => orderApi.returnStock(props.order!.id, { lineId: line.id, locationId, quantity: quantityNumber, note: note.trim() }));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("returnDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("returnDialog.confirm")}>
			<p className="sf:text-body-md">{t("returnDialog.body")}</p>
			<div className="sf:grid sf:min-w-[32rem] sf:grid-cols-1 sf:gap-3">
				<ITSelect
					name="lineId"
					label={t("returnDialog.line")}
					value={lineId}
					onChange={(e) => setLineId(e.target.value)}
					options={lines.map((candidate) => ({ value: candidate.id, label: `${candidate.product.code} · ${candidate.product.name} (${t("detail.picked")} ${formatQuantity(candidate.pickedQuantity, candidate.unit?.decimalPlaces, i18n.language)})` }))}
					valueField="value"
					labelField="label"
					error={tried && !lineId ? t("common:validation.required") : undefined}
					touched={tried}
					required
				/>
				<ITSelect
					name="locationId"
					label={t("returnDialog.location")}
					value={locationId}
					onChange={(e) => setLocationId(e.target.value)}
					options={locations.map((location) => ({ value: location.id, label: location.code }))}
					valueField="value"
					labelField="label"
					error={submit.fieldErrors.locationId ?? (tried && !locationId ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.locationId}
					required
				/>
				<ITInput
					name="quantity"
					type="number"
					label={t("returnDialog.quantity")}
					value={quantity}
					onChange={(e) => setQuantity(e.target.value)}
					error={submit.fieldErrors.quantity ?? (tried && line && quantityNumber > Number(line.pickedQuantity) ? t("transferDialog.abovePicked") : undefined)}
					touched={tried || !!submit.fieldErrors.quantity}
					required
				/>
				<ITTextarea
					name="note"
					label={t("returnDialog.note")}
					value={note}
					onChange={setNote}
					placeholder={t("returnDialog.noteHint")}
					rows={3}
					maxLength={500}
					error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
				/>
			</div>
		</FormDialog>
	);
}
