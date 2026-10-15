import { ITButton, ITInput } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { addedQuantity, captureTarget, invoiceApi, type InvoiceLine } from "@entities/invoice";
import { ProductPicker, type Product } from "@entities/product";
import { ApiError } from "@shared/api/client";

// The quick capture bar of the detail (F8-07): material and quantity, and Enter adds. A repeated material adds to the
// line that exists, and a missing field takes the focus instead of an error: the flow never stops the keyboard.
export function QuickCapture(props: {
	invoiceId: string;
	lines: InvoiceLine[];
	autoFocus?: boolean;
	onSaved: () => void;
}) {
	const { t } = useTranslation(["invoices", "common"]);
	const material = useRef<HTMLDivElement>(null);
	const quantity = useRef<HTMLDivElement>(null);
	const [product, setProduct] = useState<Product | null>(null);
	const [value, setValue] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const focusMaterial = () => material.current?.querySelector<HTMLInputElement>("input")?.focus();
	const focusQuantity = () => quantity.current?.querySelector<HTMLInputElement>("input")?.focus();

	// Arriving from `Guardar y agregar materiales`, the material is waiting with the focus on it.
	useEffect(() => {
		if (props.autoFocus) focusMaterial();
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
	const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
		const target = event.target as HTMLElement;
		if (event.key !== "Enter" || target.tagName === "TEXTAREA") return;
		if (target.getAttribute("name") === "capture-product" && !product) return;
		event.preventDefault();
		void add();
	};

	return (
		<div className="sf:flex sf:flex-col sf:gap-2" onKeyDown={onKeyDown} data-testid="quick-capture">
			<div className="sf:grid sf:grid-cols-1 sf:items-end sf:gap-3 sf:md:grid-cols-[1fr_9rem_auto]">
				<div ref={material}>
					<ProductPicker name="capture-product" label={t("form.product")} value={product?.id ?? ""} onChange={setProduct} />
				</div>
				<div ref={quantity}>
					<ITInput
						name="capture-quantity"
						type="number"
						label={t("form.quantity")}
						value={value}
						onChange={(event) => setValue(String(event.target.value))}
					/>
				</div>
				<ITButton label={t("capture.add")} onClick={() => void add()} disabled={busy} />
			</div>
			{error && <p className="sf:text-body-md sf:text-danger">{error}</p>}
		</div>
	);
}
