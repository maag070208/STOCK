import { ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { orderActions, orderApi, type Order, type OrderAction } from "@entities/order";
import { useCan } from "@entities/user";
import { AcceptDifferenceDialog, AssignOrderDialog, CancelOrderDialog, ReassignOperatorDialog, ReturnPicksDialog, TransferPicksDialog, ValidateOrderDialog } from "@features/order/OrderDialogs";
import { ApiError } from "@shared/api/client";
import { formatDate, formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";
import { STATUS_COLOR } from "./OrdersPage";

// One order: its header, what each line asked for and how much has left, and the actions its status and the person's
// permissions allow (order-fulfillment.md sections 6, 7a and 8). A validated order is locked: its receipt is all that is
// left, and every other action is gone.
export function OrderPage() {
	const { t, i18n } = useTranslation(["orders", "common"]);
	const { id = "" } = useParams();
	const navigate = useNavigate();
	const can = {
		assign: useCan("orders.assign"),
		validate: useCan("orders.validate"),
		cancel: useCan("orders.cancel"),
		reassign: useCan("orders.reassign"),
		acceptDifference: useCan("orders.accept_difference"),
		view: useCan("orders.view"),
		locations: useCan("locations.view"),
	};
	const [order, setOrder] = useState<Order | null>(null);
	const [missing, setMissing] = useState(false);
	const [open, setOpen] = useState<OrderAction | "assign" | null>(null);
	const [downloading, setDownloading] = useState(false);

	const load = useCallback(async () => {
		try {
			setOrder(await orderApi.get(id));
		} catch {
			setMissing(true);
		}
	}, [id]);

	useEffect(() => {
		void load();
	}, [load]);

	const back = <ITButton variant="outlined" label={t("detail.back")} onClick={() => navigate("/orders")} />;
	if (missing) {
		return (
			<ITPage title={t("title")} noPadding>
				<p className="sf:text-body-md">{t("common:errors.notFound")}</p>
				{back}
			</ITPage>
		);
	}

	const lines = order?.lines ?? [];
	const actions = order ? orderActions({ status: order.status.code, hasOperator: !!order.operator, lines, can }) : [];
	const quantity = (value: number, decimals: number | null | undefined) => formatQuantity(value, decimals ?? null, i18n.language);

	// The receipt is a private file of the API: it is asked for with the session and offered as a download.
	async function downloadDocument() {
		if (!order) return;
		setDownloading(true);
		try {
			const blob = await orderApi.document(order.id);
			const url = URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = `comprobante-${order.number}.pdf`;
			document.body.appendChild(link);
			link.click();
			link.remove();
			// The browser reads the file after the click, so the URL is released later.
			window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"));
		} finally {
			setDownloading(false);
		}
	}

	const saved = () => {
		setOpen(null);
		toast(t("common:saved"));
		void load();
	};

	return (
		<ITPage
			title={order ? `${order.number} · ${order.customer.name ?? ""}` : t("title")}
			noPadding
			loading={!order}
			actions={
				<div className="sf:flex sf:flex-wrap sf:gap-2">
					{back}
					{actions.includes("assign") && <ITButton label={t("detail.assign")} onClick={() => setOpen("assign")} />}
					{actions.includes("reassignOperator") && <ITButton variant="outlined" label={t("detail.reassignOperator")} onClick={() => setOpen("reassignOperator")} />}
					{actions.includes("transfer") && <ITButton variant="outlined" label={t("detail.transfer")} onClick={() => setOpen("transfer")} />}
					{actions.includes("returnStock") && <ITButton variant="outlined" label={t("detail.returnStock")} onClick={() => setOpen("returnStock")} />}
					{actions.includes("acceptDifference") && <ITButton variant="outlined" label={t("detail.acceptDifference")} onClick={() => setOpen("acceptDifference")} />}
					{actions.includes("validate") && <ITButton label={t("detail.validate")} onClick={() => setOpen("validate")} />}
					{actions.includes("cancel") && <ITButton variant="outlined" label={t("detail.cancel")} onClick={() => setOpen("cancel")} />}
					{actions.includes("document") && <ITButton label={t("detail.document")} onClick={() => void downloadDocument()} disabled={downloading} />}
				</div>
			}
		>
			{order && (
				<div className="sf:flex sf:flex-col sf:gap-6">
					<dl className="sf:grid sf:max-w-3xl sf:grid-cols-[max-content_1fr] sf:gap-x-8 sf:gap-y-3" data-testid="order-header">
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("columns.status")}</dt>
						<dd>
							<ITBadget color={STATUS_COLOR[order.status.code]} label={order.status.label ?? order.status.code} />
						</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.customer")}</dt>
						<dd className="sf:text-body-md">{`${order.customer.code ?? ""} · ${order.customer.name ?? ""}`}</dd>
						{order.contact && (
							<>
								<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.contact")}</dt>
								<dd className="sf:text-body-md">{order.contact.name}</dd>
							</>
						)}
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.warehouse")}</dt>
						<dd className="sf:text-body-md">{order.warehouse.code}</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.dueDate")}</dt>
						<dd className="sf:text-body-md">{formatDate(order.dueDate, i18n.language)}</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.createdBy")}</dt>
						<dd className="sf:text-body-md">{order.createdBy?.name ?? ""}</dd>
						<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.operator")}</dt>
						<dd className="sf:text-body-md" data-testid="order-operator">
							{order.operator?.name ?? t("detail.noOperator")}
						</dd>
						{order.validatedBy && (
							<>
								<dt className="sf:text-label-md sf:text-on-surface-muted">{t("detail.validatedBy")}</dt>
								<dd className="sf:text-body-md">{order.validatedBy.name}</dd>
							</>
						)}
					</dl>

					<section aria-label={t("detail.lines")} className="sf:flex sf:flex-col sf:gap-3" data-testid="order-lines">
						<h2 className="sf:text-title-md">{t("detail.lines")}</h2>
						{lines.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("detail.noLines")}</p>}
						{lines.length > 0 && (
							<table className="sf:w-full sf:max-w-3xl sf:text-left sf:text-body-md">
								<thead>
									<tr className="sf:text-label-md sf:text-on-surface-muted">
										<th className="sf:px-2 sf:py-1">{t("form.product")}</th>
										<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.quantity")}</th>
										<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.picked")}</th>
										<th className="sf:px-2 sf:py-1 sf:text-right">{t("detail.pending")}</th>
									</tr>
								</thead>
								<tbody>
									{lines.map((line) => (
										<tr key={line.id} className="sf:border-t sf:border-outline-variant" data-line={line.product.code ?? ""}>
											<td className="sf:px-2 sf:py-1">{`${line.product.code} · ${line.product.name}`}</td>
											<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums">{quantity(line.quantity, line.unit?.decimalPlaces)}</td>
											<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums" data-picked={line.product.code ?? ""}>
												{quantity(line.pickedQuantity, line.unit?.decimalPlaces)}
											</td>
											<td className="sf:px-2 sf:py-1 sf:text-right sf:tabular-nums">{quantity(Number(line.quantity) - Number(line.pickedQuantity), line.unit?.decimalPlaces)}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</section>
				</div>
			)}

			<AssignOrderDialog order={order} isOpen={open === "assign"} title={t("assignDialog.title")} onClose={() => setOpen(null)} onSaved={saved} />
			<ReassignOperatorDialog order={order} isOpen={open === "reassignOperator"} onClose={() => setOpen(null)} onSaved={saved} />
			<TransferPicksDialog order={order} isOpen={open === "transfer"} onClose={() => setOpen(null)} onSaved={saved} />
			<ReturnPicksDialog order={order} isOpen={open === "returnStock"} onClose={() => setOpen(null)} onSaved={saved} />
			<AcceptDifferenceDialog order={order} isOpen={open === "acceptDifference"} onClose={() => setOpen(null)} onSaved={saved} />
			<ValidateOrderDialog order={order} isOpen={open === "validate"} onClose={() => setOpen(null)} onSaved={saved} />
			<CancelOrderDialog order={order} isOpen={open === "cancel"} onClose={() => setOpen(null)} onSaved={saved} />
		</ITPage>
	);
}
