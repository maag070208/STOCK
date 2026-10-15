import { ITAlert, ITBadget, ITButton, ITChip, ITDataTable, ITEmptyState, ITInput, ITPage, type Column, type ITDataTableFetchParams } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaSearch } from "react-icons/fa";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "@app/store";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { invoiceApi, invoiceFilters, statusChip, type Invoice, type InvoiceStatus, type Ref, type StatusChip } from "@entities/invoice";
import { useCan } from "@entities/user";
import { NewInvoiceDialog } from "@features/invoice/NewInvoiceDialog";
import { formatDate } from "@shared/lib/format";

// The color of each status of an invoice: open is still being captured, closed is done.
export const STATUS_COLOR: Record<InvoiceStatus, "gray" | "warning" | "info" | "success"> = { OPEN: "gray", PENDING: "warning", IN_ASSIGNMENT: "info", CLOSED: "success" };

// The invoices the person can see: every one with ALL, those of their warehouses with AREA (the API decides). One search
// box and three status chips drive the query (F8-05), and a new invoice is captured in a modal over the list (F8-06).
export function InvoicesPage() {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const navigate = useNavigate();
	const canCreate = useCan("invoices.create");
	const [params, setParams] = useSearchParams();
	const [search, setSearch] = useState("");
	const [applied, setApplied] = useState("");
	const [chip, setChip] = useState<StatusChip>(statusChip(params.get("status")));
	const [statuses, setStatuses] = useState<CatalogItem[]>([]);
	const [total, setTotal] = useState<number | null>(null);
	const [creating, setCreating] = useState(false);
	const [warehouses, setWarehouses] = useState<Ref[]>([]);
	const [created, setCreated] = useState<Invoice | null>(null);
	// Bumped when an invoice is created, so the table asks for its page again and the new row is there.
	const [reload, setReload] = useState(0);

	useEffect(() => {
		void catalogApi.items("INVOICE_STATUS").then(setStatuses).catch(() => setStatuses([]));
	}, []);

	useEffect(() => {
		if (!canCreate) return;
		void invoiceApi.warehouseOptions().then(setWarehouses).catch(() => setWarehouses([]));
	}, [canCreate]);

	// The box is read as it is typed, and the query waits a moment: the field never loses the focus, and the list is not
	// asked once per key.
	useEffect(() => {
		const wait = window.setTimeout(() => setApplied(search.trim()), 300);
		return () => window.clearTimeout(wait);
	}, [search]);

	// `N` opens the modal from anywhere on the list, unless the person is writing in a field or a dialog is open.
	useEffect(() => {
		if (!canCreate) return;
		const onKey = (event: KeyboardEvent) => {
			if (event.key.toLowerCase() !== "n" || event.metaKey || event.ctrlKey || event.altKey) return;
			const target = event.target as HTMLElement;
			if (target.closest("input, select, textarea, [contenteditable='true']")) return;
			if (document.querySelector('[role="dialog"]')) return;
			event.preventDefault();
			setCreating(true);
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [canCreate]);

	const fetchData = useCallback(
		async (query: ITDataTableFetchParams) => {
			const page = await invoiceApi.list({
				page: query.page,
				limit: query.limit,
				filters: invoiceFilters({ search: applied, chip }),
				...(query.sort ? { sort: query.sort } : {}),
			});
			setTotal(page.total);
			return page;
		},
		[applied, chip],
	);

	// The chip lives in the address, so a filtered list can be shared and the back button works.
	const choose = (next: StatusChip) => {
		setChip(next);
		setParams(next === "all" ? {} : { status: next }, { replace: true });
	};

	const clear = () => {
		setSearch("");
		choose("all");
	};

	const columns: Column<Invoice>[] = [
		{ key: "number", label: t("columns.number"), type: "string", sortable: true },
		{ key: "supplier", label: t("columns.supplier"), type: "string", render: (invoice) => invoice.supplier.name ?? "" },
		{ key: "warehouse", label: t("columns.warehouse"), type: "string", render: (invoice) => invoice.warehouse.code ?? "" },
		{ key: "invoiceDate", label: t("columns.date"), type: "string", sortable: true, render: (invoice) => formatDate(invoice.invoiceDate, i18n.language) },
		{
			key: "status",
			label: t("columns.status"),
			type: "catalog",
			catalogOptions: { data: statuses.map((status) => ({ id: status.code, name: status.label ?? status.code })) },
			render: (invoice) => <ITBadget color={STATUS_COLOR[invoice.status.code]} label={invoice.status.label ?? invoice.status.code} />,
		},
	];

	const chips: { id: StatusChip; label: string }[] = [
		{ id: "all", label: t("list.chipAll") },
		{ id: "open", label: t("list.chipOpen") },
		{ id: "closed", label: t("list.chipClosed") },
	];

	return (
		<ITPage title={t("title")} description={t("description")} noPadding actions={canCreate ? <ITButton label={t("new")} onClick={() => setCreating(true)} /> : undefined}>
			<div className="sf:flex sf:flex-col sf:gap-4">
				{created && (
					<ITAlert variant="success" dismissible onDismiss={() => setCreated(null)}>
						<div className="sf:flex sf:flex-wrap sf:items-center sf:justify-between sf:gap-2">
							<span>{t("modal.created", { number: created.number })}</span>
							<ITButton size="sm" label={t("modal.addMaterials")} onClick={() => navigate(`/invoices/${created.id}`, { state: { focus: "capture" } })} />
						</div>
					</ITAlert>
				)}

				<div className="sf:flex sf:flex-wrap sf:items-end sf:justify-between sf:gap-3">
					<div className="sf:w-96 sf:max-w-full">
						<ITInput name="search" label={t("list.search")} value={search} onChange={(event) => setSearch(String(event.target.value))} iconLeft={<FaSearch aria-hidden />} />
					</div>
					<div className="sf:flex sf:items-center sf:gap-2" role="group" aria-label={t("list.statusFilter")}>
						{chips.map((option) => (
							<ITChip key={option.id} label={option.label} selected={chip === option.id} onClick={() => choose(option.id)} />
						))}
					</div>
				</div>

				{/* The empty state is shown instead of the table, and the table stays mounted: it is what asks for the next
				    page when the filters change, so clearing them brings the rows back. */}
				{total === 0 && <ITEmptyState title={t("list.empty")} action={<ITButton variant="outlined" label={t("list.clearFilters")} onClick={clear} />} />}
				<div className={total === 0 ? "sf:hidden" : undefined}>
					<ITDataTable<Invoice>
						columns={columns}
						fetchData={fetchData}
						reloadTrigger={`${applied}|${chip}|${reload}`}
						variant="striped"
						layout="fixed"
						onRowClick={(invoice) => navigate(`/invoices/${invoice.id}`)}
					/>
				</div>
			</div>

			<NewInvoiceDialog
				isOpen={creating}
				warehouses={warehouses}
				onClose={() => setCreating(false)}
				onCreated={(invoice, action) => {
					setCreating(false);
					if (action === "materials") {
						navigate(`/invoices/${invoice.id}`, { state: { focus: "capture" } });
						return;
					}
					setCreated(invoice);
					setReload((count) => count + 1);
					toast(t("modal.created", { number: invoice.number }));
				}}
			/>
		</ITPage>
	);
}
