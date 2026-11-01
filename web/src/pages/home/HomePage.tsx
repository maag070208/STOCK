import {
	ITAlert,
	ITBadget,
	ITBentoDonut,
	ITBentoGauge,
	ITBentoGrid,
	ITBentoPill,
	ITBentoPillRow,
	ITBentoStatStrip,
	ITBentoTile,
	ITBentoWaves,
	ITBentoWeekBars,
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
import { FaBalanceScale, FaBoxes, FaChartPie, FaClipboardList, FaFileInvoice, FaRegCalendarAlt, FaTruckLoading } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { invoiceApi, receivingApi, STATUS_COLOR, type Invoice, type InvoiceStatus, type Ref } from "@entities/invoice";
import { orderApi } from "@entities/order";
import { productApi } from "@entities/product";
import { useCan, useSessionUser } from "@entities/user";
import { NewInvoiceDialog } from "@features/invoice/NewInvoiceDialog";
import { CountUp } from "@shared/ui/CountUp";
import { BENTO_TONE } from "@shared/ui/theme";

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
	allOrders: number | null;
	materials: number | null;
}

const EMPTY: Counts = { receiving: null, placement: null, completed: null, cancelled: null, discrepancies: null, orders: null, allOrders: null, materials: null };

// The invoices the home reads for the week's bars, the API's largest page, and how many open ones it lists.
const WEEK_PAGE = 200;
const QUEUE = 5;
const DAY = 86_400_000;

// A share of a whole, for a gauge or a wave. An empty whole is an empty share.
const share = (part: number | null, whole: number | null) => (part && whole ? part / whole : 0);

// How many invoices were captured on each of the last seven days, today last.
function weekOf(invoices: Invoice[], locale: string, now = new Date()) {
	const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
	const weekday = new Intl.DateTimeFormat(locale, { weekday: "short" });
	return Array.from({ length: 7 }, (_, index) => {
		const start = midnight - (6 - index) * DAY;
		const value = invoices.filter(({ createdAt }) => {
			const at = new Date(createdAt).getTime();
			return at >= start && at < start + DAY;
		}).length;
		return { label: weekday.format(start), value, tone: index === 6 ? ("today" as const) : ("past" as const) };
	});
}

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
	const canCreateInvoice = useCan("invoices.create");
	const canCreateOrder = useCan("orders.create");

	const [counts, setCounts] = useState<Counts>(EMPTY);
	const [queue, setQueue] = useState<Invoice[]>([]);
	const [week, setWeek] = useState<ReturnType<typeof weekOf>>([]);
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

		const [receiving, placement, completed, cancelled, discrepancies, orders, allOrders, materials, weekInvoices, pendingInvoices] = await Promise.all([
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: RECEIVING } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: READY_TO_PLACE } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: COMPLETED } })) : Promise.resolve(null),
			canInvoices ? count(invoiceApi.list({ page: 1, limit: 1, filters: { status: CANCELLED } })) : Promise.resolve(null),
			canResolve ? count(receivingApi.discrepancies({ page: 1, limit: 1, filters: {} })) : Promise.resolve(null),
			canOrders ? count(orderApi.open()) : Promise.resolve(null),
			canOrders ? count(orderApi.query({ page: 1, limit: 1, filters: {} })) : Promise.resolve(null),
			canProducts ? count(productApi.query({ page: 1, limit: 1, filters: { active: "true" } })) : Promise.resolve(null),
			canInvoices ? latest(invoiceApi.list({ page: 1, limit: WEEK_PAGE, filters: {}, sort: { key: "createdAt", direction: "desc" } })) : Promise.resolve([]),
			// What waits longest comes first.
			canInvoices ? latest(invoiceApi.list({ page: 1, limit: QUEUE, filters: { status: [...RECEIVING, ...READY_TO_PLACE] }, sort: { key: "createdAt", direction: "asc" } })) : Promise.resolve([]),
		]);

		setCounts({ receiving, placement, completed, cancelled, discrepancies, orders, allOrders, materials });
		setQueue(pendingInvoices);
		setWeek(weekOf(weekInvoices, i18n.language));
		setFailed(broken);
		setLoading(false);
	}, [canInvoices, canResolve, canOrders, canProducts, i18n.language]);

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

	const inProcess = counts.receiving === null || counts.placement === null ? null : counts.receiving + counts.placement;
	const hasDiscrepancies = (counts.discrepancies ?? 0) > 0;
	const statusSegments = [
		{ label: t("home.strip.receiving"), value: counts.receiving ?? 0, color: "var(--sf-color-warning)" },
		{ label: t("home.strip.placement"), value: counts.placement ?? 0, color: "var(--sf-color-info)" },
		{ label: t("home.strip.completed"), value: counts.completed ?? 0, color: "var(--sf-color-success)" },
		{ label: t("home.strip.cancelled"), value: counts.cancelled ?? 0, color: "var(--sf-color-danger)" },
	];
	const invoiceTotal = statusSegments.reduce((sum, segment) => sum + segment.value, 0);
	// The order the tiles of the mosaic rise in, one after the other.
	const rise = (place: number) => ({ className: "sf-rise", style: { animationDelay: `${place * 70}ms` } });

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
					{canCreateOrder && <ITButton variant="outlined" label={t("home.actions.newOrder")} onClick={() => navigate("/orders/new")} />}
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

			{/* The operation as a mosaic: one tone per meaning, and a visual only where it says something about the number. */}
			{(canInvoices || canResolve || canOrders || canProducts) && (
				<ITBentoGrid>
					{canInvoices && (
						<ITBentoTile
							{...rise(0)}
							label={t("home.tiles.inProcess")}
							icon={<FaTruckLoading aria-hidden />}
							cols={4}
							rows={4}
							wide
							tone={BENTO_TONE.ink}
							value={<CountUp value={inProcess} />}
							caption={t("home.tiles.inProcessCaption")}
							onClick={() => navigate("/invoices?status=open")}
						>
							<ITStack spacing={2} className="sf:my-1 sf:min-h-0 sf:flex-1 sf:justify-center">
								<ITBentoGauge value={share(counts.completed, (counts.completed ?? 0) + (inProcess ?? 0))} color="var(--sf-color-primary)" />
								<ITBentoPillRow>
									<ITBentoPill dot="var(--sf-color-warning)">{t("home.tiles.receivingPill", { count: counts.receiving ?? 0 })}</ITBentoPill>
									<ITBentoPill dot="var(--sf-color-primary)">{t("home.tiles.placementPill", { count: counts.placement ?? 0 })}</ITBentoPill>
								</ITBentoPillRow>
							</ITStack>
						</ITBentoTile>
					)}
					{canOrders && (
						<ITBentoTile
							{...rise(1)}
							label={t("home.tiles.orders")}
							icon={<FaClipboardList aria-hidden />}
							cols={5}
							rows={2}
							wide
							tone={BENTO_TONE.orange}
							value={<CountUp value={counts.orders} />}
							caption={t("home.tiles.ordersOf", { total: counts.allOrders ?? 0 })}
							onClick={() => navigate("/orders")}
						>
							<ITBentoWaves level={share(counts.orders, counts.allOrders)} color="var(--sf-color-primary-warm)" />
						</ITBentoTile>
					)}
					{canResolve && (
						<ITBentoTile
							{...rise(2)}
							label={t("home.tiles.discrepancies")}
							icon={<FaBalanceScale aria-hidden />}
							cols={3}
							rows={2}
							tone={hasDiscrepancies ? BENTO_TONE.amber : "plain"}
							value={<CountUp value={counts.discrepancies} />}
							caption={hasDiscrepancies ? t("home.tiles.discrepanciesCaption") : t("home.tiles.noDiscrepancies")}
							onClick={() => navigate("/discrepancies")}
						>
							{hasDiscrepancies && <ITBentoWaves level={share(counts.discrepancies, inProcess)} color="var(--sf-color-primary-warm)" />}
						</ITBentoTile>
					)}
					{canInvoices && (
						<ITBentoTile {...rise(3)} label={t("home.tiles.week")} icon={<FaRegCalendarAlt aria-hidden />} cols={5} rows={2} wide>
							<ITBentoWeekBars items={week} color="var(--sf-color-primary-warm)" todayColor="var(--sf-color-primary)" />
						</ITBentoTile>
					)}
					{canProducts && (
						<ITBentoTile
							{...rise(4)}
							label={t("home.tiles.materials")}
							icon={<FaBoxes aria-hidden />}
							cols={3}
							rows={2}
							value={<CountUp value={counts.materials} />}
							caption={t("home.tiles.materialsCaption")}
							onClick={() => navigate("/products")}
						/>
					)}
				</ITBentoGrid>
			)}

			<ITGrid container columns={12} spacing={6} className="sf-rise" style={{ animationDelay: "350ms" }}>
				{canInvoices && (
					<ITGrid item xs={12} lg={8}>
						<ITCard
							className="sf:h-full"
							title={t("home.sections.queue")}
							headerActions={<ITButton size="sm" variant="text" label={t("home.actions.invoices")} onClick={() => navigate("/invoices?status=open")} />}
						>
							{!loading && queue.length === 0 ? (
								<ITEmptyState title={t("home.queueEmpty")} />
							) : (
								<ITStack spacing={1}>
									{queue.map((invoice) => (
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
				{canInvoices && (
					<ITGrid item xs={12} lg={4}>
						<ITCard className="sf:h-full" title={t("home.tiles.status")} headerActions={<FaChartPie aria-hidden />}>
							{invoiceTotal === 0 ? (
								<ITEmptyState title={t("home.tiles.noInvoices")} />
							) : (
								<ITStack spacing={4} alignItems="center">
									<ITBentoDonut segments={statusSegments} center={invoiceTotal} caption={t("home.tiles.statusTotal")} size={148} />
									<ITBentoPillRow>
										{statusSegments.map((segment) => (
											<ITBentoPill key={segment.label} dot={segment.color}>
												{segment.label} · {segment.value}
											</ITBentoPill>
										))}
									</ITBentoPillRow>
								</ITStack>
							)}
						</ITCard>
					</ITGrid>
				)}
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
