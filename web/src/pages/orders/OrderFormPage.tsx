import { ITAlert, ITButton, ITDatePicker, ITInput, ITPage, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { availableOf, lineError, orderApi, type AvailabilityItem, type OrderInput, type Ref } from "@entities/order";
import { partnerApi, type Partner } from "@entities/partner";
import { ProductPicker, type Product } from "@entities/product";
import { dyn } from "@shared/i18n";
import { formatQuantity, isoDate } from "@shared/lib/format";
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
	const [number, setNumber] = useState("");
	const [warehouseId, setWarehouseId] = useState("");
	const [customerId, setCustomerId] = useState("");
	const [contactId, setContactId] = useState("");
	const [dueDate, setDueDate] = useState(new Date());
	const [lines, setLines] = useState<LineState[]>([]);
	const [warehouses, setWarehouses] = useState<Ref[]>([]);
	const [customers, setCustomers] = useState<Partner[]>([]);
	const [customer, setCustomer] = useState<Partner | null>(null);
	const [availability, setAvailability] = useState<AvailabilityItem[]>([]);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	// Only active customers are offered for a new order (order-fulfillment.md section 3).
	useEffect(() => {
		void customersApi.query({ page: 1, limit: 200, filters: { active: "true" } }).then((page) => setCustomers(page.data));
		void orderApi.warehouseOptions().then(setWarehouses);
		// eslint-disable-next-line react-hooks/exhaustive-deps
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
		<ITPage title={t("new")} noPadding>
			<form onSubmit={(e) => void save(e)} noValidate aria-label={t("new")} className="sf:flex sf:max-w-4xl sf:flex-col sf:gap-6">
				<section className="sf:flex sf:flex-col sf:gap-3">
					<h2 className="sf:text-title-md">{t("form.header")}</h2>
					<div className="sf:grid sf:grid-cols-1 sf:gap-3 sf:md:grid-cols-2">
						<ITInput
							name="number"
							label={t("form.number")}
							value={number}
							onChange={(e) => setNumber(e.target.value.toUpperCase())}
							error={submit.fieldErrors.number ?? (tried && !number.trim() ? required : undefined) ?? t("form.numberHint")}
							touched={tried || !!submit.fieldErrors.number}
							required
						/>
						<ITDatePicker
							name="dueDate"
							label={t("form.dueDate")}
							value={dueDate}
							minDate={new Date()}
							onChange={(e) => e.target.value instanceof Date && setDueDate(e.target.value)}
							error={submit.fieldErrors.dueDate}
							touched={!!submit.fieldErrors.dueDate}
							required
						/>
						<ITSelect
							name="customerId"
							label={t("form.customer")}
							value={customerId}
							onChange={(e) => setCustomerId(e.target.value)}
							options={customers.map((found) => ({ value: found.id, label: `${found.code} · ${found.name}` }))}
							valueField="value"
							labelField="label"
							error={submit.fieldErrors.customerId ?? (tried && !customerId ? required : undefined)}
							touched={tried || !!submit.fieldErrors.customerId}
							required
						/>
						<ITSelect
							name="contactId"
							label={t("form.contact")}
							value={contactId}
							onChange={(e) => setContactId(e.target.value)}
							options={[{ value: "", label: t("form.noContact") }, ...(customer?.contacts ?? []).filter((found) => found.active).map((found) => ({ value: found.id, label: found.name }))]}
							valueField="value"
							labelField="label"
							error={submit.fieldErrors.contactId}
							touched={!!submit.fieldErrors.contactId}
						/>
						<ITSelect
							name="warehouseId"
							label={t("form.warehouse")}
							value={warehouseId}
							onChange={(e) => setWarehouseId(e.target.value)}
							options={warehouses.map((found) => ({ value: found.id, label: `${found.code} · ${found.name}` }))}
							valueField="value"
							labelField="label"
							error={submit.fieldErrors.warehouseId ?? (tried && !warehouseId ? required : undefined)}
							touched={tried || !!submit.fieldErrors.warehouseId}
							required
						/>
					</div>
				</section>

				<section className="sf:flex sf:flex-col sf:gap-3" aria-label={t("form.lines")}>
					<div className="sf:flex sf:items-center sf:justify-between">
						<h2 className="sf:text-title-md">{t("form.lines")}</h2>
						<ITButton size="sm" variant="outlined" label={t("form.addLine")} onClick={() => setLines((current) => [...current, newLine()])} />
					</div>
					{lines.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("form.noLines")}</p>}
					{lines.map((line, index) => {
						const error = errorOf(line);
						return (
							<div key={line.key} className="sf:flex sf:flex-col sf:gap-3 sf:rounded-md sf:border sf:border-outline-variant sf:p-4" data-line={index}>
								<div className="sf:grid sf:grid-cols-1 sf:items-end sf:gap-3 sf:md:grid-cols-[1fr_9rem_auto]">
									<ProductPicker
										name={`product-${index}`}
										label={t("form.product")}
										value={line.product?.id ?? ""}
										onChange={(product) => setLine(line.key, { product })}
										error={submit.fieldErrors[`lines.${index}.productId`] ?? (tried && !line.product ? required : undefined)}
										required
									/>
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
									<ITButton variant="text" label={t("form.remove")} onClick={() => setLines((current) => current.filter((candidate) => candidate.key !== line.key))} />
								</div>
								<p className="sf:text-body-md sf:text-on-surface-muted" data-available={index}>
									{availabilityText(line)}
								</p>
							</div>
						);
					})}
				</section>

				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				<div className="sf:flex sf:justify-end sf:gap-2">
					<ITButton variant="outlined" label={t("common:actions.cancel")} onClick={() => navigate("/orders")} />
					<ITButton type="submit" label={submit.submitting ? t("form.saving") : t("form.save")} disabled={submit.submitting} />
				</div>
			</form>
		</ITPage>
	);
}
