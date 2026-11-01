import {
	ITAlert,
	ITBadget,
	ITBentoGrid,
	ITBentoStatStrip,
	ITBentoTile,
	ITButton,
	ITCard,
	ITEmptyState,
	ITFlex,
	ITGrid,
	ITStack,
	ITText,
} from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaBalanceScale, FaBoxOpen, FaBoxes, FaClipboardList, FaFileInvoice } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { invoiceApi, receivingApi, STATUS_COLOR, type Invoice, type InvoiceStatus, type Ref } from "@entities/invoice";
import { orderApi } from "@entities/order";
import { productApi } from "@entities/product";
import { useCan, useSessionUser } from "@entities/user";
import { NewInvoiceDialog } from "@features/invoice/NewInvoiceDialog";

// The statuses each number of the home reads. They are the API's codes, not the labels, so the counts say the same
// thing in both locales (material-receiving.md section 8).
const RECEIVING: InvoiceStatus[] = ["OPEN", "PENDING"];
const READY_TO_PLACE: InvoiceStatus[] = ["IN_ASSIGNMENT"];
const COMPLETED: InvoiceStatus[] = ["CLOSED"];
const CANCELLED: InvoiceStatus[] = ["CANCELLED"];

interface Counts {
	receiving: number | null;
	placement: number | null;
	completed: number | null;
	cancelled: number | null;
	discrepancies: number | null;
	orders: number | null;
	materials: number | null;
}

const EMPTY: Counts = { receiving: null, placement: null, completed: null, cancelled: null, discrepancies: null, orders: null, materials: null };

// The home is the dashboard of the operation, not a menu of modules: the greeting, the numbers of the day, the work
// queues and the last invoices captured. It only reads what the person's permissions open, and each number links to the
// list it comes from (design/stockflow-DESIGN.md).
export function HomePage() {
	const { t, i18n } = useTranslation();
	const navigate = useNavigate();
	const user = useSessionUser();
	const canInvoices = useCan("invoices.view");
	const canResolve = useCan("discrepancies.resolve");
	const canOrders = useCan("orders.view");
	const canProducts = useCan("products.view");
	const canReports = useCan("reports.view");
	const canCreateInvoice = useCan("invoices.create");
	const canCreateOrder = useCan("orders.create");

	const [counts, setCounts] = useState<Counts>(EMPTY);
	const [recent, setRecent] = useState<Invoice[]>([]);
	const [warehouses, setWarehouses] = useState<Ref[]>([]);
	const [creating, setCreating] = useState(false);
	const [failed, setFailed] = useState(false);
	const [loading, setLoading] = useState(true);

	// One request per number, all at once. A number the person may not read is never asked for, and a request that
	// fails leaves its number empty instead of taking the whole dashboard down.
	const load = useCallback(async () => {
		let broken = false;
		const count = (request: Promise<{ total: number }>) =>
			request.then((page) => page.total).catch(() => {
				broken = true;
				return null;
			});
		const latest = (request: Promise<{ data: Invoice[] }>) =>
			request.then((page) => page.data).catch(() => {
				broken = true;
				return [];
			});

		const [receiving, placement, completed, cancelled, discrepancies, orders, materials, latestInvoices] = await Promise.all([
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: RECEIVING } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: READY_TO_PLACE } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: COMPLETED } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: CANCELLED } })) : Promise.resolve(null),
			canResolve ? count(receivingApi.discrepancies({ page: 1, limit: 1, filters: {} })) : Promise.resolve(null),
			canOrders ? count(orderApi.open()) : Promise.resolve(null),
			canProducts ? count(productApi.query({ page: 1, limit: 1, filters: { active: "true" } })) : Promise.resolve(null),
			canInvoices ? latest(invoiceApi.list({ page: 1, limit: 5, filters: {}, sort: { key: "createdAt", direction: "desc" } })) : Promise.resolve([]),
		]);

		setCounts({ receiving, placement, completed, cancelled, discrepancies, orders, materials });
		setRecent(latestInvoices);
		setFailed(broken);
		setLoading(false);
	}, [canInvoices, canResolve, canOrders, canProducts]);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		if (!canCreateInvoice) return;
		void invoiceApi.warehouseOptions().then(setWarehouses).catch(() => setWarehouses([]));
	}, [canCreateInvoice]);

	const show = (value: number | null) => (value === null ? "—" : new Intl.NumberFormat(i18n.language).format(value));
	const longDate = new Intl.DateTimeFormat(i18n.language, { weekday: "long", day: "numeric", month: "long" }).format(new Date());
	const dateLabel = longDate.charAt(0).toUpperCase() + longDate.slice(1);
	const firstName = (user?.name ?? "").split(" ")[0];

	// The numbers of the invoice strip are also the filters of the list: each one opens the invoices of its status.
	const strip = [
		{ id: "receiving", label: t("home.strip.receiving"), value: show(counts.receiving), color: "var(--sf-color-warning)" },
		{ id: "placement", label: t("home.strip.placement"), value: show(counts.placement), color: "var(--sf-color-info)" },
		{ id: "completed", label: t("home.strip.completed"), value: show(counts.completed), color: "var(--sf-color-success)" },
		{ id: "cancelled", label: t("home.strip.cancelled"), value: show(counts.cancelled), color: "var(--sf-color-danger)" },
	];
	const chipOf = (id: string) => (id === "receiving" ? "open" : id);

	return (
		<ITStack spacing={6} className="sf:pt-2">
			{/* The greeting: the date of the day and the one action that creates work. */}
			<ITFlex align="end" justify="between" wrap="wrap" gap={4}>
				<ITStack spacing={1}>
					<ITText as="h1" className="sf:text-headline-xl">
						{t("home.greeting", { name: firstName })}
					</ITText>
					<ITText className="sf:text-body-md" muted>
						{t("home.subtitle")} · {dateLabel}
					</ITText>
				</ITStack>
				<ITFlex align="center" gap={2} wrap="wrap">
					{canInvoices && <ITButton variant="outlined" label={t("home.actions.invoices")} onClick={() => navigate("/invoices")} />}
					{canCreateInvoice && <ITButton label={t("home.actions.newInvoice")} icon={<FaFileInvoice aria-hidden />} onClick={() => setCreating(true)} />}
				</ITFlex>
			</ITFlex>

			{failed && (
				<ITAlert variant="warning">
					<ITFlex align="center" justify="between" wrap="wrap" gap={3}>
						<ITText>{t("home.loadError")}</ITText>
						<ITButton size="sm" variant="outlined" label={t("home.actions.retry")} onClick={() => void load()} />
					</ITFlex>
				</ITAlert>
			)}

			{/* The work of the invoices, in one strip that is also the filter of the list. */}
			{canInvoices && <ITBentoStatStrip items={strip} onSelect={(id) => navigate(`/invoices?status=${chipOf(id)}`)} />}

			{/* The rest of the operation, as pastel tiles: one tone per meaning, never a saturated card. */}
			{(canInvoices || canResolve || canOrders || canProducts) && (
				<ITBentoGrid>
					{canInvoices && (
						<ITBentoTile
							label={t("home.tiles.placement")}
							icon={<FaBoxOpen aria-hidden />}
							cols={3}
							rows={2}
							wide
							tone="mint"
							value={show(counts.placement)}
							caption={t("home.tiles.placementCaption")}
							onClick={() => navigate("/invoices?status=placement")}
						/>
					)}
					{canResolve && (
						<ITBentoTile
							label={t("home.tiles.discrepancies")}
							icon={<FaBalanceScale aria-hidden />}
							cols={3}
							rows={2}
							wide
							tone="sun"
							value={show(counts.discrepancies)}
							caption={t("home.tiles.discrepanciesCaption")}
							onClick={() => navigate("/discrepancies")}
						/>
					)}
					{canOrders && (
						<ITBentoTile
							label={t("home.tiles.orders")}
							icon={<FaClipboardList aria-hidden />}
							cols={3}
							rows={2}
							wide
							tone="lavender"
							value={show(counts.orders)}
							caption={t("home.tiles.ordersCaption")}
							onClick={() => navigate("/orders")}
						/>
					)}
					{canProducts && (
						<ITBentoTile
							label={t("home.tiles.materials")}
							icon={<FaBoxes aria-hidden />}
							cols={3}
							rows={2}
							wide
							tone="plain"
							value={show(counts.materials)}
							caption={t("home.tiles.materialsCaption")}
							onClick={() => navigate("/products")}
						/>
					)}
				</ITBentoGrid>
			)}

			<ITGrid container columns={12} spacing={6}>
				{canInvoices && (
					<ITGrid item xs={12} lg={8}>
						<ITCard
							title={t("home.sections.latest")}
							headerActions={<ITButton size="sm" variant="text" label={t("home.actions.invoices")} onClick={() => navigate("/invoices")} />}
						>
							{!loading && recent.length === 0 ? (
								<ITEmptyState title={t("home.emptyLatest")} />
							) : (
								<ITStack spacing={1}>
									{recent.map((invoice) => (
										<ITButton
											key={invoice.id}
											variant="text"
											className="sf:w-full sf:justify-start sf:px-2 sf:py-2.5 sf:font-normal"
											onClick={() => navigate(`/invoices/${invoice.id}`)}
										>
											<ITFlex align="center" gap={3} className="sf:w-full">
												<ITFlex align="center" justify="center" className="sf:size-9 sf:shrink-0 sf:rounded-pill sf:bg-surface-soft sf:text-on-surface-muted">
													<FaFileInvoice aria-hidden />
												</ITFlex>
												<ITStack spacing={0} alignItems="start" className="sf:min-w-0 sf:flex-1">
													<ITText className="sf:truncate sf:text-title-sm sf:text-on-background">{invoice.number}</ITText>
													<ITText className="sf:truncate sf:text-label-sm" muted>
														{invoice.supplier.name}
													</ITText>
												</ITStack>
												<ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />
											</ITFlex>
										</ITButton>
									))}
								</ITStack>
							)}
						</ITCard>
					</ITGrid>
				)}
				<ITGrid item xs={12} lg={canInvoices ? 4 : 12}>
					<ITCard title={t("home.sections.quick")}>
						<ITStack spacing={2}>
							{canCreateInvoice && <ITButton label={t("home.actions.newInvoice")} onClick={() => setCreating(true)} />}
							{canCreateOrder && <ITButton variant="outlined" label={t("home.actions.newOrder")} onClick={() => navigate("/orders/new")} />}
							{canOrders && <ITButton variant="outlined" label={t("home.tiles.orders")} onClick={() => navigate("/orders")} />}
							{canReports && <ITButton variant="outlined" label={t("home.actions.reports")} onClick={() => navigate("/reports/stock")} />}
						</ITStack>
					</ITCard>
				</ITGrid>
			</ITGrid>

			{canCreateInvoice && (
				<NewInvoiceDialog
					isOpen={creating}
					warehouses={warehouses}
					onClose={() => setCreating(false)}
					onCreated={(invoice) => {
						setCreating(false);
						navigate(`/invoices/${invoice.id}`, { state: { focus: "capture" } });
					}}
				/>
			)}
		</ITStack>
	);
}
