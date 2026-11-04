import { ITAlert, ITSearchSelect, ITButton, ITCard, ITDatePicker, ITFlex, ITGrid, ITInput, ITPage, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { FaTrash } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { availableOf, lineError, repeatedProducts, orderApi, type AvailabilityItem, type OrderInput, type Ref } from "@entities/order";
import { PartnerPicker, partnerApi, type Partner } from "@entities/partner";
import { WarehousePicker } from "@entities/warehouse";
import { ProductPicker, type Product } from "@entities/product";
import { dyn } from "@shared/i18n";
import { businessToday, formatQuantity, isoDate } from "@shared/lib/format";
import { newId } from "@shared/lib/newId";
import { IconButton } from "@shared/ui/IconButton";
import { QuantityInput } from "@shared/ui/QuantityInput";
import { useSubmit } from "@shared/ui/useSubmit";

interface LineState {
	key: string;
	product: Product | null;
	quantity: string;
}

const newLine = (): LineState => ({ key: newId(), product: null, quantity: "" });

// A new order (order-fulfillment.md sections 3 to 5): its header, and each material with what the warehouse has of it.
// The screen shows the availability the API answers for the chosen warehouse and keeps every quantity at or below it;
// the creation checks it again under the lock, so a line above it never reserves what is not there.
export function OrderFormPage() {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const navigate = useNavigate();
	const customersApi = partnerApi("customers");
	// One key for this form: a repeated submission of the same order creates it once.
	const key = useRef(newId());
	const [warehouses, setWarehouses] = useState<Ref[]>([]);
	// A number ready to use, from the moment of the order; the office can write its own.
	const [number, setNumber] = useState(() => `ORD-${isoDate(new Date()).replaceAll("-", "").slice(2)}-${newId().slice(0, 4).toUpperCase()}`);
	const [warehouseId, setWarehouseId] = useState("");
	const [customerId, setCustomerId] = useState("");
	const [contactId, setContactId] = useState("");
	// The day the business is in, not the browser's: the API refuses a due date before its own today.
	const [dueDate, setDueDate] = useState(businessToday());
	const [lines, setLines] = useState<LineState[]>(() => [newLine()]);
	const [customer, setCustomer] = useState<Partner | null>(null);
	const [availability, setAvailability] = useState<AvailabilityItem[]>([]);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		// The warehouses this screen may use, from the module's own options endpoint.
		void orderApi
			.warehouseOptions()
			.then((options) => {
				setWarehouses(options);
				// With one warehouse there is nothing to choose.
				if (options.length === 1) setWarehouseId(options[0].id);
			})
			.catch(() => setWarehouses([]));
	}, []);

	useEffect(() => {
		setContactId("");
		setCustomer(null);
		if (customerId) void customersApi.get(customerId).then(setCustomer);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [customerId]);

	// The materials of the lines of this warehouse, in one query: their availability, as the API answers it. `refresh`
	// asks it again after a refused creation: someone else may have taken stock while the form was open, and the numbers
	// it shows must be the ones the API just judged against.
	const [refresh, setRefresh] = useState(0);
	const productIds = useMemo(() => lines.map((line) => line.product?.id).filter((id): id is string => !!id), [lines]);
	const wanted = productIds.join(",");
	useEffect(() => {
		setAvailability([]);
		if (!warehouseId || !wanted) return;
		let live = true;
		void orderApi
			.availability(warehouseId, wanted.split(","))
			.then((answer) => {
				if (live) setAvailability(answer.items);
			})
			.catch(() => undefined);
		return () => {
			live = false;
		};
	}, [warehouseId, wanted, refresh]);

	const setLine = (lineKey: string, change: Partial<LineState>) => setLines((current) => current.map((line) => (line.key === lineKey ? { ...line, ...change } : line)));
	// Enter on a quantity moves to the next material, opening a line when it was the last.
	const form = useRef<HTMLFormElement>(null);
	const nextLine = (index: number) => {
		if (index === lines.length - 1) setLines((current) => [...current, newLine()]);
		window.setTimeout(() => form.current?.querySelector<HTMLInputElement>(`[name="product-${index + 1}"]`)?.focus());
	};
	const available = (line: LineState) => availableOf(availability, line.product?.id);
	const repeated = repeatedProducts(lines.map((line) => line.product?.id));
	// A line answers for itself while it is written, without waiting for the first attempt to save: the mistake is said
	// as it is made. The header fields wait for `tried`, so an empty form does not shout.
	const errorOf = (line: LineState): ReturnType<typeof lineError> | "FIELD_REQUIRED" => {
		if (!line.product) return "FIELD_REQUIRED";
		if (repeated.has(line.product.id)) return "DUPLICATE_PRODUCT";
		return lineError(line.quantity, available(line), line.product.unit.decimalPlaces ?? 0);
	};
	// The line that was never touched is the next one waiting, not a mistake.
	const filled = lines.filter((line) => line.product || line.quantity);
	const firstLineError = filled.map((line) => errorOf(line)).find((error) => error !== null) ?? null;
	// The lines answer live, so their problems close the button from the moment they exist; the header fields still
	// need the first attempt to say what is missing, and `save` checks everything once more.
	const lineBlocked = filled.length === 0 || firstLineError !== null;
	const blocked = lineBlocked || !number.trim() || !warehouseId || !customerId;
	const required = t("common:validation.required");

	// The availability of a line as it is written: the number, or a dash while the API has not answered.
	const availabilityText = (line: LineState) => {
		if (!warehouseId) return t("form.chooseWarehouse");
		const value = available(line);
		return value === null ? t("form.availableUnknown") : t("form.available", { quantity: formatQuantity(value, line.product?.unit.decimalPlaces ?? null, i18n.language) });
	};

	async function save(event: FormEvent) {
		event.preventDefault();
		setTried(true);
		if (!number.trim() || !warehouseId || !customerId || blocked) return;
		const input: OrderInput = {
			number: number.trim(),
			warehouseId,
			customerId,
			contactId: contactId || null,
			dueDate: isoDate(dueDate),
			lines: filled.map((line) => ({ productId: line.product!.id, quantity: Number(line.quantity) })),
		};
		const saved = await submit.run(() => orderApi.create(input, key.current));
		if (saved) navigate(`/orders/${saved.id}`, { replace: true });
		else setRefresh((tick) => tick + 1);
	}

	return (
		<ITPage title={t("new")} breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title"), onClick: () => navigate("/orders") }, { label: t("new") })} noPadding>
			<form
				ref={form}
				onSubmit={(e) => void save(e)}
				onKeyDown={(event) => {
					const name = (event.target as HTMLElement).getAttribute("name") ?? "";
					if (event.key !== "Enter" || !name.startsWith("quantity-")) return;
					event.preventDefault();
					nextLine(Number(name.slice("quantity-".length)));
				}}
				noValidate
				aria-label={t("new")}
				className="sf:flex sf:flex-col sf:gap-6"
			>
				<ITCard title={t("form.header")}>
					<ITGrid container spacing={3}>
						<ITGrid item xs={12} md={4}>
							<ITInput
								name="number"
							label={t("form.number")}
							value={number}
							onChange={(e) => setNumber(e.target.value.toUpperCase())}
								error={submit.fieldErrors.number ?? (tried && !number.trim() ? required : undefined) ?? t("form.numberHint")}
								touched={tried || !!submit.fieldErrors.number}
								required
							/>
						</ITGrid>
						<ITGrid item xs={12} md={4}>
							<ITDatePicker
								name="dueDate"
							label={t("form.dueDate")}
							value={dueDate}
							minDate={businessToday()}
							onChange={(e) => e.target.value instanceof Date && setDueDate(e.target.value)}
								error={submit.fieldErrors.dueDate}
								touched={!!submit.fieldErrors.dueDate}
								required
							/>
						</ITGrid>
						<ITGrid item xs={12} md={4}>
							<WarehousePicker
								options={warehouses}
								name="warehouseId"
								label={t("form.warehouse")}
								value={warehouseId}
								onChange={(chosen) => setWarehouseId(chosen)}
								error={submit.fieldErrors.warehouseId ?? (tried && !warehouseId ? required : undefined)}
								required
							/>
						</ITGrid>
						<ITGrid item xs={12} md={6}>
							<PartnerPicker
								kind="customers"
								name="customerId"
								label={t("form.customer")}
								value={customerId}
								onChange={(chosen) => {
									setCustomerId(chosen?.id ?? "");
									setContactId("");
								}}
								error={submit.fieldErrors.customerId ?? (tried && !customerId ? required : undefined)}
								required
							/>
						</ITGrid>
						<ITGrid item xs={12} md={6}>
							<ITSearchSelect
								name="contactId"
								label={t("form.contact")}
								value={contactId}
								onChange={(value) => setContactId(String(value))}
								options={(customer?.contacts ?? []).filter((found) => found.active).map((found) => ({ value: found.id, label: found.name }))}
								error={submit.fieldErrors.contactId}
								touched={!!submit.fieldErrors.contactId}
							/>
						</ITGrid>
					</ITGrid>
				</ITCard>

				{/* One row per material: material, quantity and what the warehouse has. Enter opens the next one. */}
				<ITCard
					title={t("form.lines")}
					headerActions={<ITButton size="sm" variant="outlined" label={t("form.addLine")} onClick={() => setLines((current) => [...current, newLine()])} />}
				>
					<ITStack spacing={3}>
						{lines.length === 0 && <ITText muted>{t("form.noLines")}</ITText>}
						{lines.map((line, index) => {
							const error = errorOf(line);
							// A line with something in it answers while it is written; an empty one waits.
							const dirty = !!(line.product || line.quantity);
							const touched = (tried && (line.product || line.quantity || filled.length === 0)) || dirty;
							return (
								<ITGrid key={line.key} container spacing={3} className="sf:items-start">
									<ITGrid item xs={12} md={6}>
										<ProductPicker
											name={`product-${index}`}
											label={t("form.product")}
											value={line.product?.id ?? ""}
											onChange={(product) => {
												setLine(line.key, { product });
												if (product) window.setTimeout(() => form.current?.querySelector<HTMLInputElement>(`[name="quantity-${index}"]`)?.focus());
											}}
											exclude={productIds.filter((id) => id !== line.product?.id)}
											error={submit.fieldErrors[`lines.${index}.productId`] ?? (touched && !line.product ? required : undefined)}
											required
										/>
									</ITGrid>
									<ITGrid item xs={8} md={2}>
										<QuantityInput
											name={`quantity-${index}`}
											label={line.product ? `${t("form.quantity")} (${line.product.unit.label ?? line.product.unit.code})` : t("form.quantity")}
											value={line.quantity}
											onChange={(quantity) => setLine(line.key, { quantity })}
											decimalPlaces={line.product?.unit.decimalPlaces ?? 0}
											max={available(line)}
											error={submit.fieldErrors[`lines.${index}.quantity`] ?? (touched && error ? t(dyn(`lineErrors.${error}`)) : undefined)}
											touched={!!touched || !!submit.fieldErrors[`lines.${index}.quantity`]}
											required
										/>
									</ITGrid>
									<ITGrid item xs={12} md={3} className="sf:self-center">
										{line.product && <ITText muted>{availabilityText(line)}</ITText>}
									</ITGrid>
									<ITGrid item xs={4} md={1} className="sf:self-center">
										<IconButton icon={<FaTrash aria-hidden />} label={t("form.remove")} color="danger" onClick={() => setLines((current) => current.filter((candidate) => candidate.key !== line.key))} />
									</ITGrid>
								</ITGrid>
							);
						})}
						<ITText muted className="sf:text-label-sm">{t("form.moreHint")}</ITText>
					</ITStack>
				</ITCard>

				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				{lineBlocked && (
					<ITAlert variant="warning">{filled.length === 0 ? t("form.noLines") : firstLineError ? t(dyn(`lineErrors.${firstLineError}`)) : t("form.noLines")}</ITAlert>
				)}
				<ITFlex justify="end" gap={2}>
					<ITButton variant="outlined" label={t("common:actions.cancel")} onClick={() => navigate("/orders")} />
					<ITButton type="submit" label={submit.submitting ? t("form.saving") : t("form.save")} disabled={submit.submitting || lineBlocked} />
				</ITFlex>
			</form>
		</ITPage>
	);
}
