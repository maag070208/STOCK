import { ITAlert, ITButton, ITCheckbox, ITDatePicker, ITInput, ITPage, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { invoiceApi, sharesError, staffApi, type Ref, type ShareInput, type StaffMember } from "@entities/invoice";
import { partnerApi, type Partner } from "@entities/partner";
import { ProductPicker, type Product } from "@entities/product";
import { SharesEditor } from "@features/invoice/SharesEditor";
import { isoDate } from "@shared/lib/format";
import { newId } from "@shared/lib/newId";
import { useSubmit } from "@shared/ui/useSubmit";

interface LineState {
	key: string;
	product: Product | null;
	quantity: string;
	assignNow: boolean;
	shares: ShareInput[];
}

const newLine = (): LineState => ({ key: newId(), product: null, quantity: "", assignNow: false, shares: [] });

// A new invoice (material-entry.md sections 3 to 5): its header, its materials, and, optionally, who receives each one
// before saving (path B). Without shares it is saved OPEN and assigned on its detail (path A).
export function InvoiceFormPage() {
	const { t } = useTranslation(["invoices", "common"]);
	const navigate = useNavigate();
	const suppliersApi = partnerApi("suppliers");
	// One key for this form: a repeated submission of the same invoice creates it once.
	const key = useRef(newId());
	const [number, setNumber] = useState("");
	const [supplierId, setSupplierId] = useState("");
	const [invoiceDate, setInvoiceDate] = useState(new Date());
	const [warehouseId, setWarehouseId] = useState("");
	const [lines, setLines] = useState<LineState[]>([]);
	const [suppliers, setSuppliers] = useState<Partner[]>([]);
	const [warehouses, setWarehouses] = useState<Ref[]>([]);
	const [staff, setStaff] = useState<StaffMember[]>([]);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	// Only active suppliers are offered for a new invoice (suppliers.md section 2).
	useEffect(() => {
		void suppliersApi.query({ page: 1, limit: 200, filters: { active: "true" } }).then((page) => setSuppliers(page.data));
		void invoiceApi.warehouseOptions().then(setWarehouses);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	useEffect(() => {
		setStaff([]);
		if (warehouseId) void staffApi.list(warehouseId).then(setStaff).catch(() => setStaff([]));
	}, [warehouseId]);

	const setLine = (lineKey: string, change: Partial<LineState>) => setLines((current) => current.map((line) => (line.key === lineKey ? { ...line, ...change } : line)));
	const required = t("common:validation.required");
	const fieldError = (field: string, missing: boolean) => submit.fieldErrors[field] ?? (tried && missing ? required : undefined);
	const blocked = lines.some((line) => !line.product || !(Number(line.quantity) > 0) || (line.assignNow && sharesError(line.quantity, line.shares) !== null));

	async function save(event: FormEvent) {
		event.preventDefault();
		setTried(true);
		if (!number.trim() || !supplierId || !warehouseId || blocked) return;
		const input = {
			number,
			supplierId,
			invoiceDate: isoDate(invoiceDate),
			warehouseId,
			lines: lines.map((line) => ({
				productId: line.product!.id,
				quantity: Number(line.quantity),
				...(line.assignNow ? { assignments: line.shares.map((s) => ({ operatorId: s.operatorId, quantity: Number(s.quantity) })) } : {}),
			})),
		};
		const saved = await submit.run(() => invoiceApi.create(input, key.current));
		if (saved) navigate(`/invoices/${saved.id}`, { replace: true });
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
							error={fieldError("number", !number.trim()) ?? t("form.numberHint")}
							touched={!!fieldError("number", !number.trim())}
							required
						/>
						<ITDatePicker
							name="invoiceDate"
							label={t("form.date")}
							value={invoiceDate}
							maxDate={new Date()}
							onChange={(e) => e.target.value instanceof Date && setInvoiceDate(e.target.value)}
							error={submit.fieldErrors.invoiceDate}
							touched={!!submit.fieldErrors.invoiceDate}
							required
						/>
						<ITSelect
							name="supplierId"
							label={t("form.supplier")}
							value={supplierId}
							onChange={(e) => setSupplierId(e.target.value)}
							options={suppliers.map((s) => ({ value: s.id, label: `${s.code} · ${s.name}` }))}
							valueField="value"
							labelField="label"
							error={fieldError("supplierId", !supplierId)}
							touched={!!fieldError("supplierId", !supplierId)}
							required
						/>
						<ITSelect
							name="warehouseId"
							label={t("form.warehouse")}
							value={warehouseId}
							onChange={(e) => setWarehouseId(e.target.value)}
							options={warehouses.map((w) => ({ value: w.id, label: `${w.code} · ${w.name}` }))}
							valueField="value"
							labelField="label"
							error={fieldError("warehouseId", !warehouseId)}
							touched={!!fieldError("warehouseId", !warehouseId)}
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
					{lines.map((line, index) => (
						<div key={line.key} className="sf:flex sf:flex-col sf:gap-3 sf:rounded-md sf:border sf:border-outline-variant sf:p-4" data-line={index}>
							<div className="sf:grid sf:grid-cols-1 sf:items-end sf:gap-3 sf:md:grid-cols-[1fr_9rem_auto]">
								<ProductPicker
									name={`product-${index}`}
									label={t("form.product")}
									value={line.product?.id ?? ""}
									onChange={(product) => setLine(line.key, { product })}
									error={fieldError(`lines.${index}.productId`, !line.product)}
									required
								/>
								<ITInput
									name={`quantity-${index}`}
									type="number"
									label={line.product ? `${t("form.quantity")} (${line.product.unit.label ?? line.product.unit.code})` : t("form.quantity")}
									value={line.quantity}
									onChange={(e) => setLine(line.key, { quantity: e.target.value })}
									error={fieldError(`lines.${index}.quantity`, !(Number(line.quantity) > 0))}
									touched={!!fieldError(`lines.${index}.quantity`, !(Number(line.quantity) > 0))}
									required
								/>
								<ITButton variant="text" label={t("form.remove")} onClick={() => setLines((current) => current.filter((l) => l.key !== line.key))} />
							</div>
							<ITCheckbox
								name={`assign-${index}`}
								label={t("form.assignNow")}
								checked={line.assignNow}
								onChange={(checked) => setLine(line.key, { assignNow: checked, shares: line.shares.length ? line.shares : [{ operatorId: "", quantity: line.quantity }] })}
							/>
							{line.assignNow && (
								<SharesEditor
									name={`shares-${index}`}
									line={line.quantity}
									decimals={line.product?.unit.decimalPlaces ?? null}
									staff={staff}
									shares={line.shares}
									onChange={(shares) => setLine(line.key, { shares })}
									showErrors={tried}
									emptyMessage={warehouseId ? undefined : t("form.chooseWarehouse")}
								/>
							)}
							{submit.fieldErrors[`lines.${index}.assignments`] && <p className="sf:text-body-md sf:text-danger">{submit.fieldErrors[`lines.${index}.assignments`]}</p>}
						</div>
					))}
				</section>

				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				<div className="sf:flex sf:justify-end sf:gap-2">
					<ITButton variant="outlined" label={t("common:actions.cancel")} onClick={() => navigate("/invoices")} />
					<ITButton type="submit" label={submit.submitting ? t("form.saving") : t("form.save")} disabled={submit.submitting} />
				</div>
			</form>
		</ITPage>
	);
}
