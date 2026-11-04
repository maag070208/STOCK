import { ITBadget, ITButton, ITChip, ITFlex, ITSearchSelect, ITStack, ITText, ITTextarea } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { orderApi, type Order, type OrderLine, type OrderOperator } from "@entities/order";
import { locationApi, type Location } from "@entities/warehouse";
import { formatQuantity } from "@shared/lib/format";
import { FormDialog } from "@shared/ui/FormDialog";
import { QuantityInput } from "@shared/ui/QuantityInput";
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

	const required = t("common:validation.required");

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={props.title}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting}
			blocked={operatorId ? null : `${t("assignDialog.operator")}: ${required}`}
			error={submit.error}
		>
			<ITStack className="sf:min-w-[24rem]">
				<ITSearchSelect
					name="operatorId"
					label={t("assignDialog.operator")}
					value={operatorId}
					onChange={(value) => setOperatorId(String(value))}
					options={operators.map((operator) => ({ value: operator.id, label: operator.name }))}
					valueField="value"
					labelField="label"
					error={submit.fieldErrors.operatorId ?? (tried && !operatorId ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.operatorId}
					required
				/>
			</ITStack>
			{operators.length === 0 && <ITText muted>{t("assignDialog.noStaff")}</ITText>}
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

	const required = t("common:validation.required");

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={t("reassignDialog.title")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting}
			blocked={!operatorId ? `${t("reassignDialog.operator")}: ${required}` : !note.trim() ? `${t("reassignDialog.note")}: ${required}` : null}
			error={submit.error}
		>			<ITText>{props.order?.operator ? t("reassign.current", { name: props.order.operator.name }) : t("reassignDialog.body")}</ITText>
			<ITStack spacing={3} className="sf:min-w-[26rem]">
				<ITSearchSelect
					name="operatorId"
					label={t("reassignDialog.operator")}
					value={operatorId}
					onChange={(value) => setOperatorId(String(value))}
					options={operators.filter((operator) => operator.id !== props.order?.operator?.id).map((operator) => ({ value: operator.id, label: operator.name }))}
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
			</ITStack>
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
			<ITText>{t("validateDialog.body")}</ITText>
			<ITStack className="sf:min-w-[28rem]">
				<ITTextarea name="note" label={t("validateDialog.note")} value={note} onChange={setNote} placeholder={t("validateDialog.noteHint")} rows={3} maxLength={500} />
			</ITStack>
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
			<ITText>{t("differenceDialog.body")}</ITText>
			<ITStack className="sf:min-w-[28rem]">
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
			</ITStack>
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
			<ITText>{t("cancelDialog.body")}</ITText>
		</FormDialog>
	);
}

// The material a reassignment moves: already known when the line opened the dialog, or chosen among what was picked.
function PickedLine(props: { lines: OrderLine[]; line: OrderLine | null; label: string; tried: boolean; onChoose: (lineId: string) => void }) {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const picked = (line: OrderLine) => formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces, i18n.language);
	if (props.lines.length === 1 && props.line) {
		return (
			<ITFlex align="center" justify="between" gap={3} className="sf:rounded-xl sf:bg-surface-soft sf:p-3">
				<ITText className="sf:min-w-0 sf:text-title-sm">{`${props.line.product.code} · ${props.line.product.name}`}</ITText>
				<ITBadget color="warning" label={`${t("detail.picked")} ${picked(props.line)}`} />
			</ITFlex>
		);
	}
	return (
		<ITSearchSelect
			name="lineId"
			label={props.label}
			value={props.line?.id ?? ""}
			onChange={(value) => props.onChoose(String(value))}
			options={props.lines.map((candidate) => ({ value: candidate.id, label: `${candidate.product.code} · ${candidate.product.name} (${t("detail.picked")} ${picked(candidate)})` }))}
			valueField="value"
			labelField="label"
			error={props.tried && !props.line ? t("common:validation.required") : undefined}
			touched={props.tried}
			required
		/>
	);
}

interface Target {
	order: Order;
	needs: number;
}

// Moving what was picked to another open order of the same warehouse (section 8, action 2). No balance changes: the
// goods already left the shelves and now serve the target order. The dialog offers only the orders that need this
// material, with how much; choosing one fills in the most that can move, and a common reason is one click.
export function TransferPicksDialog(props: { order: Order | null; line: OrderLine | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const [candidates, setCandidates] = useState<Order[]>([]);
	const [targets, setTargets] = useState<Target[]>([]);
	const [loaded, setLoaded] = useState(false);
	const [lineId, setLineId] = useState("");
	const [targetId, setTargetId] = useState("");
	const [quantity, setQuantity] = useState("");
	const [note, setNote] = useState("");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	const lines = (props.order?.lines ?? []).filter((line) => Number(line.pickedQuantity) > 0);
	const line = lines.find((candidate) => candidate.id === lineId) ?? null;
	const target = targets.find((candidate) => candidate.order.id === targetId) ?? null;
	const reasons = [t("reassign.reasons.urgent"), t("reassign.reasons.priority"), t("reassign.reasons.shortage")];
	const shown = (value: number) => formatQuantity(value, line?.unit?.decimalPlaces, i18n.language);

	useEffect(() => {
		if (props.isOpen && props.order) {
			setCandidates([]);
			setLineId(props.line?.id ?? (lines.length === 1 ? lines[0].id : ""));
			setTargetId("");
			setQuantity("");
			setNote("");
			setTried(false);
			submit.reset();
			void orderApi.openOf(props.order.warehouse.id).then((page) => setCandidates(page.data.filter((order) => order.id !== props.order!.id))).catch(() => setCandidates([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order, props.line]);

	// The list carries the headers only: each candidate is read with its lines, to know what it still needs of the material.
	useEffect(() => {
		setTargets([]);
		setTargetId("");
		setLoaded(false);
		if (!line) return;
		let live = true;
		void Promise.all(candidates.map((candidate) => orderApi.get(candidate.id).catch(() => null))).then((found) => {
			if (!live) return;
			setTargets(
				found.flatMap((order): Target[] => {
					const own = order?.lines?.find((candidate) => candidate.product.id === line.product.id);
					const needs = own ? Number(own.quantity) - Number(own.pickedQuantity) : 0;
					return order && needs > 0 ? [{ order, needs }] : [];
				}),
			);
			setLoaded(true);
		});
		return () => {
			live = false;
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [candidates, line?.id]);

	const choose = (next: Target) => {
		setTargetId(next.order.id);
		setQuantity(String(Math.min(next.needs, Number(line?.pickedQuantity ?? 0))));
	};

	const quantityNumber = Number(quantity);
	const overPicked = line ? quantityNumber > Number(line.pickedQuantity) : false;
	const overTarget = target !== null && quantityNumber > target.needs;
	const quantityBad = !quantity.trim() || quantityNumber <= 0 || overPicked || overTarget;
	const quantityError =
		line && quantity.trim() ? (overTarget ? t("transferDialog.aboveTarget") : overPicked || quantityNumber <= 0 ? t("transferDialog.abovePicked") : undefined) : undefined;
	const required = t("common:validation.required");
	// The reason the dialog stays closed, said where the click would be: the first thing still missing or over.
	const blockedReason = !line
		? `${t("transferDialog.line")}: ${required}`
		: !target
			? `${t("reassign.target")}: ${required}`
			: !quantity.trim()
				? `${t("transferDialog.quantity")}: ${required}`
				: quantityBad
					? quantityError ?? null
					: !note.trim()
						? `${t("transferDialog.note")}: ${required}`
						: null;

	async function save() {
		setTried(true);
		if (!props.order || !line || !target || blockedReason || !note.trim()) return;
		const saved = await submit.run(() => orderApi.transfer(props.order!.id, { lineId: line.id, targetOrderId: target.order.id, quantity: quantityNumber, note: note.trim() }));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("transferDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} blocked={blockedReason} error={submit.error} submitLabel={t("transferDialog.confirm")}>
			<ITStack spacing={3} className="sf:min-w-[32rem]">
				<PickedLine lines={lines} line={line} label={t("transferDialog.line")} tried={tried} onChoose={setLineId} />
				{line && (
					<ITStack spacing={2}>
						<ITText className="sf:text-label-md">{t("reassign.target")}</ITText>
						{loaded && targets.length === 0 && <ITText muted>{t("reassign.noneNeed")}</ITText>}
						{tried && !target && targets.length > 0 && <ITText className="sf:text-label-sm sf:text-danger">{t("common:validation.required")}</ITText>}
						{targets.map((candidate) => (
							<ITButton key={candidate.order.id} variant={candidate.order.id === targetId ? "filled" : "outlined"} className="sf:w-full sf:justify-start sf:font-normal" onClick={() => choose(candidate)}>
								<ITFlex align="center" justify="between" gap={3} className="sf:w-full">
									<ITText as="span" className="sf:min-w-0 sf:truncate">{`${candidate.order.number} · ${candidate.order.customer.name ?? ""}`}</ITText>
									<ITText as="span" className="sf:shrink-0 sf:text-label-sm">{t("reassign.needs", { quantity: shown(candidate.needs) })}</ITText>
								</ITFlex>
							</ITButton>
						))}
					</ITStack>
				)}
				{target && (
					<>
						<QuantityInput
							name="quantity"
							label={t("transferDialog.quantity")}
							value={quantity}
							onChange={setQuantity}
							decimalPlaces={line?.unit?.decimalPlaces ?? 0}
							min={1}
							max={line ? Math.min(target.needs, Number(line.pickedQuantity)) : null}
							error={submit.fieldErrors.quantity ?? quantityError ?? t("reassign.max", { quantity: shown(Math.min(target.needs, Number(line?.pickedQuantity ?? 0))) })}
							touched={!!quantityError || !!submit.fieldErrors.quantity}
							required
						/>
						<ITStack spacing={2}>
							<ITText className="sf:text-label-md">{t("reassign.reason")}</ITText>
							<ITFlex wrap="wrap" gap={2}>
								{reasons.map((reason) => (
									<ITChip key={reason} label={reason} selected={note === reason} onClick={() => setNote(reason)} />
								))}
							</ITFlex>
						</ITStack>
						<ITTextarea
							name="note"
							label={t("transferDialog.note")}
							value={note}
							onChange={setNote}
							placeholder={t("transferDialog.noteHint")}
							rows={2}
							maxLength={500}
							error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
						/>
					</>
				)}
			</ITStack>
		</FormDialog>
	);
}

// Returning what was picked to a location of the warehouse (section 8, action 3): the only reassignment that changes a
// balance, with a movement in. It starts with everything that was picked of the line.
export function ReturnPicksDialog(props: { order: Order | null; line: OrderLine | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["orders", "common"]);
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
			const first = props.line ?? (lines.length === 1 ? lines[0] : null);
			setLocations([]);
			setLineId(first?.id ?? "");
			setLocationId("");
			setQuantity(first ? String(Number(first.pickedQuantity)) : "");
			setNote("");
			setTried(false);
			submit.reset();
			void locationApi
				.query({ page: 1, limit: 200, filters: { warehouseId: props.order.warehouse.id, active: "true" } })
				.then((page) => setLocations(page.data))
				.catch(() => setLocations([]));
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.order, props.line]);

	const quantityNumber = Number(quantity);
	const overPicked = line ? quantityNumber > Number(line.pickedQuantity) : false;
	const quantityError = line && quantity.trim() ? (quantityNumber <= 0 || overPicked ? t("transferDialog.abovePicked") : undefined) : undefined;
	const required = t("common:validation.required");
	// The first thing still missing or over, said where the click would be.
	const blockedReason = !line
		? `${t("returnDialog.line")}: ${required}`
		: !locationId
			? `${t("returnDialog.location")}: ${required}`
			: !quantity.trim()
				? `${t("returnDialog.quantity")}: ${required}`
				: quantityNumber <= 0 || overPicked
					? (quantityError ?? null)
					: !note.trim()
						? `${t("returnDialog.note")}: ${required}`
						: null;

	async function save() {
		setTried(true);
		if (!props.order || !line || !locationId || blockedReason) return;
		const saved = await submit.run(() => orderApi.returnStock(props.order!.id, { lineId: line.id, locationId, quantity: quantityNumber, note: note.trim() }));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog isOpen={props.isOpen} title={t("returnDialog.title")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} blocked={blockedReason} error={submit.error} submitLabel={t("returnDialog.confirm")}>
			<ITStack spacing={3} className="sf:min-w-[32rem]">
				<PickedLine
					lines={lines}
					line={line}
					label={t("returnDialog.line")}
					tried={tried}
					onChoose={(id) => {
						setLineId(id);
						setQuantity(String(Number(lines.find((candidate) => candidate.id === id)?.pickedQuantity ?? "")));
					}}
				/>
				<ITSearchSelect
					name="locationId"
					label={t("returnDialog.location")}
					value={locationId}
					onChange={(value) => setLocationId(String(value))}
					options={locations.map((location) => ({ value: location.id, label: location.code }))}
					valueField="value"
					labelField="label"
					error={submit.fieldErrors.locationId ?? (tried && !locationId ? t("common:validation.required") : undefined)}
					touched={tried || !!submit.fieldErrors.locationId}
					required
				/>
				<QuantityInput
					name="quantity"
					label={t("returnDialog.quantity")}
					value={quantity}
					onChange={setQuantity}
					decimalPlaces={line?.unit?.decimalPlaces ?? 0}
					min={1}
					max={line ? Number(line.pickedQuantity) : null}
					error={submit.fieldErrors.quantity ?? quantityError}
					touched={!!quantityError || !!submit.fieldErrors.quantity}
					required
				/>
				<ITTextarea
					name="note"
					label={t("returnDialog.note")}
					value={note}
					onChange={setNote}
					placeholder={t("returnDialog.noteHint")}
					rows={2}
					maxLength={500}
					error={submit.fieldErrors.note ?? (tried && !note.trim() ? t("common:validation.required") : undefined)}
				/>
			</ITStack>
		</FormDialog>
	);
}
