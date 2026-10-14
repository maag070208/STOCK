import { ITInput, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { copiesError, printApi, type PrintablePrinter } from "@entities/label";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { toast } from "@app/store";

// What a label is printed for: a product, on any printer the person prints on, or a location, on a printer of its
// own warehouse (labels.md section 6).
export type PrintTarget =
	| { kind: "PRODUCT"; productId: string; title: string }
	| { kind: "LOCATION"; locationId: string; warehouseId: string; title: string };

export function PrintDialog(props: { target: PrintTarget | null; onClose: () => void }) {
	const { t } = useTranslation(["labels", "common"]);
	const [printers, setPrinters] = useState<PrintablePrinter[] | null>(null);
	const [printerId, setPrinterId] = useState("");
	const [copies, setCopies] = useState("1");
	const [tried, setTried] = useState(false);
	const submit = useSubmit();
	const target = props.target;

	useEffect(() => {
		if (!target) return;
		setCopies("1");
		setTried(false);
		setPrinters(null);
		submit.reset();
		void printApi
			.printers()
			.then((all) => {
				const usable = target.kind === "LOCATION" ? all.filter((p) => p.warehouseId === target.warehouseId) : all;
				setPrinters(usable);
				setPrinterId(usable.length === 1 ? usable[0].id : "");
			})
			.catch(() => setPrinters([]));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [target]);

	const local = {
		printerId: !printerId ? t("common:validation.required") : undefined,
		copies: ((code) => (code === "required" ? t("common:validation.required") : code ? t("print.copiesRange") : undefined))(copiesError(copies)),
	};
	const errors = {
		printerId: submit.fieldErrors.printerId ?? (tried ? local.printerId : undefined),
		copies: submit.fieldErrors.copies ?? (tried ? local.copies : undefined),
	};

	async function print() {
		setTried(true);
		if (!target || local.printerId || local.copies) return;
		const count = Number(copies.trim());
		const printed = await submit.run(() =>
			printApi.print({
				templateKey: target.kind,
				printerId,
				copies: count,
				...(target.kind === "PRODUCT" ? { productId: target.productId } : { locationId: target.locationId }),
			}),
		);
		if (printed) {
			toast(t("print.sent", { printer: printers?.find((p) => p.id === printerId)?.name ?? "", count }));
			props.onClose();
		}
	}

	const empty = printers !== null && printers.length === 0;
	return (
		<FormDialog isOpen={!!target} title={t("print.title")} onClose={props.onClose} onSubmit={() => void print()} submitting={submit.submitting || printers === null || empty} error={submit.error} submitLabel={t("print.submit")}>
			<p className="sf:text-body-md">
				<span className="sf:text-on-surface-muted">{target?.kind === "LOCATION" ? t("print.location") : t("print.product")}: </span>
				{target?.title}
			</p>
			{empty ? (
				<p className="sf:text-body-md sf:text-on-surface-muted">{target?.kind === "LOCATION" ? t("print.noPrintersHere") : t("print.noPrinters")}</p>
			) : (
				<ITSelect
					name="printerId"
					label={t("print.printer")}
					value={printerId}
					onChange={(e) => setPrinterId(e.target.value)}
					options={(printers ?? []).map((p) => ({ value: p.id, label: p.warehouseCode ? `${p.name} · ${p.warehouseCode}` : p.name }))}
					valueField="value"
					labelField="label"
					error={errors.printerId}
					touched={!!errors.printerId}
				/>
			)}
			<ITInput
				name="copies"
				type="number"
				label={t("print.copies")}
				value={copies}
				// No min or max: the kit would silently clamp what was typed, and the person must see the rule instead.
				// The kit's number field can hand back a number: the form keeps the text the person typed.
				onChange={(e) => setCopies(String(e.target.value ?? ""))}
				error={errors.copies}
				touched={!!errors.copies}
				required
			/>
		</FormDialog>
	);
}
