import { ITAlert, ITSearchSelect, ITButton, ITCard, ITDatePicker, ITFlex, ITGrid, ITInput, ITPage, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { availableOf, lineError, orderApi, type AvailabilityItem, type OrderInput, type Ref } from "@entities/order";
import { PartnerPicker, partnerApi, type Partner } from "@entities/partner";
import { WarehousePicker } from "@entities/warehouse";
import { ProductPicker, type Product } from "@entities/product";
import { dyn } from "@shared/i18n";
import { businessToday, formatQuantity, isoDate } from "@shared/lib/format";
import { newId } from "@shared/lib/newId";
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
	const [number, setNumber] = useState("");
	const [warehouseId, setWarehouseId] = useState("");
	const [customerId, setCustomerId] = useState("");
	const [contactId, setContactId] = useState("");
	// The day the business is in, not the browser's: the API refuses a due date before its own today.
	const [dueDate, setDueDate] = useState(businessToday());
	const [lines, setLines] = useState<LineState[]>([]);
	const [customer, setCustomer] = useState<Partner | null>(null);
	const [availability, setAvailability] = useState<AvailabilityItem[]>([]);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	useEffect(() => {
		// The warehouses this screen may use, from the module's own options endpoint.
		void orderApi.warehouseOptions().then(setWarehouses).catch(() => setWarehouses([]));
	}, []);

	useEffect(() => {
		setContactId("");
		setCustomer(null);
		if (customerId) void customersApi.get(customerId).then(setCustomer);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [customerId]);

	// The materials of the lines of this warehouse, in one query: their availability, as the API answers it.
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
	}, [warehouseId, wanted]);

	const setLine = (lineKey: string, change: Partial<LineState>) => setLines((current) => current.map((line) => (line.key === lineKey ? { ...line, ...change } : line)));
	const available = (line: LineState) => availableOf(availability, line.product?.id);
	const errorOf = (line: LineState) => (line.product ? lineError(line.quantity, available(line)) : "FIELD_REQUIRED");
	const blocked = lines.length === 0 || lines.some((line) => errorOf(line) !== null);
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
			lines: lines.map((line) => ({ productId: line.product!.id, quantity: Number(line.quantity) })),
		};
		const saved = await submit.run(() => orderApi.create(input, key.current));
		if (saved) navigate(`/orders/${saved.id}`, { replace: true });
	}

	return (
		<ITPage title={t("new")} breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("title"), onClick: () => navigate("/orders") }, { label: t("new") })} noPadding>
			<form onSubmit={(e) => void save(e)} noValidate aria-label={t("new")} className="sf:flex sf:max-w-4xl sf:flex-col sf:gap-6">
				<ITStack spacing={3}>
					<ITText as="h2" className="sf:text-title-md">
						{t("form.header")}
					</ITText>
					<ITGrid container spacing={3}>
						<ITGrid item xs={12} md={6}>
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
						<ITGrid item xs={12} md={6}>
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
						<ITGrid item xs={12} md={6}>
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
					</ITGrid>
				</ITStack>

				<ITStack spacing={3}>
					<ITFlex align="center" justify="between">
						<ITText as="h2" className="sf:text-title-md">
							{t("form.lines")}
						</ITText>
						<ITButton size="sm" variant="outlined" label={t("form.addLine")} onClick={() => setLines((current) => [...current, newLine()])} />
					</ITFlex>
					{lines.length === 0 && <ITText muted>{t("form.noLines")}</ITText>}
					{lines.map((line, index) => {
						const error = errorOf(line);
						return (
							<ITCard key={line.key} title={t("form.line", { index: index + 1 })}>
								<ITGrid container spacing={3} className="sf:items-end">
									<ITGrid item xs={12} md={6}>
										<ProductPicker
											name={`product-${index}`}
											label={t("form.product")}
											value={line.product?.id ?? ""}
											onChange={(product) => setLine(line.key, { product })}
											error={submit.fieldErrors[`lines.${index}.productId`] ?? (tried && !line.product ? required : undefined)}
											required
										/>
									</ITGrid>
									<ITGrid item xs={12} md={3}>
										<ITInput
											name={`quantity-${index}`}
											type="number"
											label={line.product ? `${t("form.quantity")} (${line.product.unit.label ?? line.product.unit.code})` : t("form.quantity")}
											value={line.quantity}
											onChange={(e) => setLine(line.key, { quantity: e.target.value })}
											error={submit.fieldErrors[`lines.${index}.quantity`] ?? (tried && error ? t(dyn(`lineErrors.${error}`)) : undefined)}
											touched={tried || !!submit.fieldErrors[`lines.${index}.quantity`]}
											required
										/>
									</ITGrid>
									<ITGrid item xs={12} md={3} className="sf:self-end">
										<ITButton variant="text" label={t("form.remove")} onClick={() => setLines((current) => current.filter((candidate) => candidate.key !== line.key))} />
									</ITGrid>
								</ITGrid>
								<ITText muted>{availabilityText(line)}</ITText>
							</ITCard>
						);
					})}
				</ITStack>

				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				<ITFlex justify="end" gap={2}>
					<ITButton variant="outlined" label={t("common:actions.cancel")} onClick={() => navigate("/orders")} />
					<ITButton type="submit" label={submit.submitting ? t("form.saving") : t("form.save")} disabled={submit.submitting} />
				</ITFlex>
			</form>
		</ITPage>
	);
}
