import { ITAlert, ITButton, ITGrid, ITInput, ITStack } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { addedQuantity, captureTarget, invoiceApi, type InvoiceLine } from "@entities/invoice";
import { ProductPicker, type Product } from "@entities/product";
import { ApiError } from "@shared/api/client";

// The quick capture bar of the detail (F8-07): material and quantity, and Enter adds. A repeated material adds to the
// line that exists, and a missing field takes the focus instead of an error: the flow never stops the keyboard. The bar
// is a form, so Enter is what the button does, and every piece of it is a component of the UI kit (web.md section 8).
export function QuickCapture(props: {
	invoiceId: string;
	lines: InvoiceLine[];
	autoFocus?: boolean;
	onSaved: () => void;
}) {
	const { t } = useTranslation(["invoices", "common"]);
	const bar = useRef<HTMLFormElement>(null);
	const [product, setProduct] = useState<Product | null>(null);
	const [value, setValue] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const focusField = (name: string) => bar.current?.querySelector<HTMLInputElement>(`[name="${name}"]`)?.focus();
	const focusMaterial = () => focusField("capture-product");
	const focusQuantity = () => focusField("capture-quantity");

	// Arriving from the modal of a new invoice, the material is waiting with the focus on it.
	useEffect(() => {
		if (props.autoFocus) bar.current?.querySelector<HTMLInputElement>('[name="capture-product"]')?.focus();
	}, [props.autoFocus]);

	async function add() {
		// What is missing takes the focus: the bar is never blocked by an error nobody asked for.
		if (!product) {
			focusMaterial();
			return;
		}
		if (!(Number(value) > 0)) {
			focusQuantity();
			return;
		}
		setBusy(true);
		setError(null);
		try {
			const target = captureTarget(props.lines, product.id);
			if (target.kind === "sum") {
				const line = props.lines.find((candidate) => candidate.id === target.lineId)!;
				await invoiceApi.updateLine(props.invoiceId, target.lineId, addedQuantity(line.quantity, Number(value)));
			} else {
				await invoiceApi.addLine(props.invoiceId, { productId: product.id, quantity: Number(value) });
			}
			setProduct(null);
			setValue("");
			props.onSaved();
			focusMaterial();
		} catch (failure) {
			setError(failure instanceof ApiError ? failure.message : t("common:errors.network"));
		} finally {
			setBusy(false);
		}
	}

	// Enter adds from anywhere in the bar. In the material picker it adds only once a material is chosen: without one it
	// is the picker's own Enter, which walks its list.
	const onKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
		const target = event.target as HTMLElement;
		if (event.key !== "Enter" || target.tagName === "TEXTAREA") return;
		if (target.getAttribute("name") === "capture-product" && !product) return;
		event.preventDefault();
		void add();
	};

	return (
		<form
			ref={bar}
			data-testid="quick-capture"
			noValidate
			onKeyDown={onKeyDown}
			onSubmit={(event: FormEvent) => {
				event.preventDefault();
				void add();
			}}
		>
			<ITStack spacing={2}>
				<ITGrid container spacing={3}>
					<ITGrid item xs={12} md={7}>
						<ProductPicker name="capture-product" label={t("form.product")} value={product?.id ?? ""} onChange={setProduct} />
					</ITGrid>
					<ITGrid item xs={12} md={3}>
						<ITInput name="capture-quantity" type="number" label={t("form.quantity")} value={value} onChange={(event) => setValue(String(event.target.value))} />
					</ITGrid>
					<ITGrid item xs={12} md={2} className="sf:self-end">
						<ITButton type="submit" label={t("capture.add")} disabled={busy} />
					</ITGrid>
				</ITGrid>
				{error && <ITAlert variant="error">{error}</ITAlert>}
			</ITStack>
		</form>
	);
}
