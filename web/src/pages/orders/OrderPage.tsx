import { ITAlert, ITBadget, ITButton, ITFlex, ITGrid, ITPage, ITStack, ITStatCard, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { orderActions, orderApi, type Order, type OrderAction, type OrderLine } from "@entities/order";
import { useCan } from "@entities/user";
import { AcceptDifferenceDialog, AssignOrderDialog, CancelOrderDialog, ReassignOperatorDialog, ReturnPicksDialog, TransferPicksDialog, ValidateOrderDialog } from "@features/order/OrderDialogs";
import { ApiError } from "@shared/api/client";
import { formatDate, formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";
import { STATUS_COLOR } from "./OrdersPage";
import { PlainTable } from "@shared/ui/PlainTable";

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

	if (missing) {
		return (
			<ITPage title={t("title")} breadcrumbs={[{ label: t("title"), onClick: () => navigate("/orders") }]} noPadding>
				<ITAlert variant="error">{t("common:errors.notFound")}</ITAlert>
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

	// The lines of the order: what each material asked for, how much has left, and what is still missing.
	const lineColumns: Column<OrderLine>[] = [
		{ key: "product", label: t("form.product"), type: "string", render: (line) => `${line.product.code} · ${line.product.name}` },
		{ key: "quantity", label: t("detail.quantity"), type: "string", render: (line) => quantity(line.quantity, line.unit?.decimalPlaces) },
		{ key: "picked", label: t("detail.picked"), type: "string", render: (line) => quantity(line.pickedQuantity, line.unit?.decimalPlaces) },
		{ key: "pending", label: t("detail.pending"), type: "string", render: (line) => quantity(Number(line.quantity) - Number(line.pickedQuantity), line.unit?.decimalPlaces) },
	];

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
			breadcrumbs={[{ label: t("title"), onClick: () => navigate("/orders") }, { label: order?.number ?? "" }]}
			actions={
				<ITFlex wrap="wrap" align="center" gap={2}>
					{order && <ITBadget color={STATUS_COLOR[order.status.code]} label={order.status.label ?? order.status.code} />}
					{actions.includes("assign") && <ITButton label={t("detail.assign")} onClick={() => setOpen("assign")} />}
					{actions.includes("reassignOperator") && <ITButton variant="outlined" label={t("detail.reassignOperator")} onClick={() => setOpen("reassignOperator")} />}
					{actions.includes("transfer") && <ITButton variant="outlined" label={t("detail.transfer")} onClick={() => setOpen("transfer")} />}
					{actions.includes("returnStock") && <ITButton variant="outlined" label={t("detail.returnStock")} onClick={() => setOpen("returnStock")} />}
					{actions.includes("acceptDifference") && <ITButton variant="outlined" label={t("detail.acceptDifference")} onClick={() => setOpen("acceptDifference")} />}
					{actions.includes("validate") && <ITButton label={t("detail.validate")} onClick={() => setOpen("validate")} />}
					{actions.includes("cancel") && <ITButton variant="outlined" label={t("detail.cancel")} onClick={() => setOpen("cancel")} />}
					{actions.includes("document") && <ITButton label={t("detail.document")} onClick={() => void downloadDocument()} disabled={downloading} />}
				</ITFlex>
			}
		>
			{order && (
				<ITStack spacing={6}>
					<ITGrid container spacing={4}>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.customer")} value={`${order.customer.code ?? ""} · ${order.customer.name ?? ""}`} />
						</ITGrid>
						{order.contact && (
							<ITGrid item xs={12} sm={6} lg={4}>
								<ITStatCard label={t("detail.contact")} value={order.contact.name ?? ""} />
							</ITGrid>
						)}
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.warehouse")} value={order.warehouse.code ?? ""} />
						</ITGrid>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.dueDate")} value={formatDate(order.dueDate, i18n.language)} />
						</ITGrid>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.createdBy")} value={order.createdBy?.name ?? ""} />
						</ITGrid>
						<ITGrid item xs={12} sm={6} lg={4}>
							<ITStatCard label={t("detail.operator")} value={order.operator?.name ?? t("detail.noOperator")} />
						</ITGrid>
						{order.validatedBy && (
							<ITGrid item xs={12} sm={6} lg={4}>
								<ITStatCard label={t("detail.validatedBy")} value={order.validatedBy.name ?? ""} />
							</ITGrid>
						)}
					</ITGrid>

					<ITStack spacing={3}>
						<ITText as="h2" className="sf:text-title-lg">
							{t("detail.lines")}
						</ITText>
						{lines.length === 0 ? <ITText muted>{t("detail.noLines")}</ITText> : <PlainTable<OrderLine>columns={lineColumns} data={lines} size="sm" />}
					</ITStack>
				</ITStack>
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
