import { ITButton, ITDialog, ITFlex, ITInput, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaBoxes, FaClipboardList, FaFileInvoice, FaLongArrowAltRight, FaPlus, FaSearch } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { invoiceApi } from "@entities/invoice";
import { orderApi } from "@entities/order";
import { APP_SCREENS, isScreenVisible } from "@entities/permission";
import { productApi } from "@entities/product";
import { useCan, usePermissions } from "@entities/user";

interface Result {
	id: string;
	label: string;
	hint: string;
	icon: ReactNode;
	path: string;
}

const SEARCH_LIMIT = 4;
const WAIT_MS = 200;

const matches = (text: string, query: string) =>
	text
		.normalize("NFD")
		.replace(/\p{M}/gu, "")
		.toLowerCase()
		.includes(query);

// The fastest way anywhere: Cmd/Ctrl + K, a few letters and Enter. It finds the screens this person may open, the things
// they may create, and the invoices, orders and materials by their number or name. The API still decides what they read.
export function CommandPalette() {
	const { t } = useTranslation();
	const navigate = useNavigate();
	const permissions = usePermissions();
	const can = { invoices: useCan("invoices.view"), newInvoice: useCan("invoices.create"), orders: useCan("orders.view"), newOrder: useCan("orders.create"), products: useCan("products.view") };
	const [isOpen, setOpen] = useState(false);
	const [text, setText] = useState("");
	const [found, setFound] = useState<Result[]>([]);
	const [selected, setSelected] = useState(0);
	const query = text
		.trim()
		.normalize("NFD")
		.replace(/\p{M}/gu, "")
		.toLowerCase();

	useEffect(() => {
		const onKey = (event: globalThis.KeyboardEvent) => {
			if (event.key.toLowerCase() !== "k" || !(event.metaKey || event.ctrlKey)) return;
			event.preventDefault();
			setOpen(true);
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, []);

	const fixed = useMemo<Result[]>(() => {
		const actions: Result[] = [
			...(can.newInvoice ? [{ id: "new-invoice", label: t("home.actions.newInvoice"), hint: t("palette.create"), icon: <FaPlus aria-hidden />, path: "/invoices?new=1" }] : []),
			...(can.newOrder ? [{ id: "new-order", label: t("home.actions.newOrder"), hint: t("palette.create"), icon: <FaPlus aria-hidden />, path: "/orders/new" }] : []),
		];
		const screens = APP_SCREENS.filter((screen) => isScreenVisible(permissions, screen)).map((screen) => ({
			id: screen.id,
			label: t(screen.labelKey),
			hint: t("palette.go"),
			icon: <FaLongArrowAltRight aria-hidden />,
			path: screen.path,
		}));
		// With nothing written the page offers what to create; with a word, the screen of that name comes first.
		return query ? [...screens, ...actions].filter((item) => matches(item.label, query)) : [...actions, ...screens];
	}, [can.newInvoice, can.newOrder, permissions, query, t]);

	// The records are searched a moment after the last key, and an answer that arrives late for an older search is dropped.
	useEffect(() => {
		if (query.length < 2) {
			setFound([]);
			return;
		}
		let live = true;
		const wait = window.setTimeout(() => {
			const quiet = <T,>(request: Promise<T[]>) => request.catch(() => [] as T[]);
			void Promise.all([
				can.invoices
					? quiet(
							invoiceApi.list({ page: 1, limit: SEARCH_LIMIT, filters: { search: text.trim() } }).then((page) =>
								page.data.map((invoice) => ({ id: `invoice-${invoice.id}`, label: invoice.number, hint: t("palette.invoices"), icon: <FaFileInvoice aria-hidden />, path: `/invoices/${invoice.id}` })),
							),
						)
					: [],
				can.orders
					? quiet(
							orderApi.query({ page: 1, limit: SEARCH_LIMIT, filters: { number: text.trim() } }).then((page) =>
								page.data.map((order) => ({ id: `order-${order.id}`, label: order.number, hint: t("palette.orders"), icon: <FaClipboardList aria-hidden />, path: `/orders/${order.id}` })),
							),
						)
					: [],
				can.products
					? quiet(
							productApi.lookup(text.trim()).then((answer) =>
								answer.products.slice(0, SEARCH_LIMIT).map((product) => ({ id: `product-${product.id}`, label: `${product.code} · ${product.name}`, hint: t("palette.materials"), icon: <FaBoxes aria-hidden />, path: `/products/${product.id}` })),
							),
						)
					: [],
			]).then((groups) => {
				if (live) setFound(groups.flat());
			});
		}, WAIT_MS);
		return () => {
			live = false;
			window.clearTimeout(wait);
		};
	}, [query, text, can.invoices, can.orders, can.products, t]);

	const results = [...fixed, ...found];
	const close = () => {
		setOpen(false);
		setText("");
		setSelected(0);
	};
	const go = (result: Result) => {
		close();
		navigate(result.path);
	};

	// The arrows walk the results and Enter opens the one selected, with the focus staying in the box.
	useEffect(() => {
		if (!isOpen) return;
		const onKey = (event: globalThis.KeyboardEvent) => {
			if (event.key === "ArrowDown" || event.key === "ArrowUp") {
				event.preventDefault();
				const step = event.key === "ArrowDown" ? 1 : -1;
				setSelected((current) => (results.length === 0 ? 0 : (current + step + results.length) % results.length));
			}
			if (event.key === "Enter" && results[selected]) {
				event.preventDefault();
				go(results[selected]);
			}
		};
		// Before the dialog, which keeps the keys it receives for its own focus.
		window.addEventListener("keydown", onKey, true);
		return () => window.removeEventListener("keydown", onKey, true);
	});

	useEffect(() => {
		document.querySelector('[data-palette-selected="true"]')?.scrollIntoView({ block: "nearest" });
	}, [selected]);

	return (
		<>
			<ITButton variant="text" className="sf:w-80 sf:max-w-full sf:justify-start sf:bg-surface-soft sf:font-normal sf:text-on-surface-muted sf:[&>span]:w-full" onClick={() => setOpen(true)}>
				<ITFlex align="center" gap={2} className="sf:w-full">
					<FaSearch aria-hidden />
					<ITText as="span" className="sf:flex-1 sf:text-left sf:text-on-surface-muted">{t("palette.open")}</ITText>
					<ITText as="span" className="sf:text-label-sm sf:text-on-surface-muted">⌘K</ITText>
				</ITFlex>
			</ITButton>
			<ITDialog isOpen={isOpen} onClose={close} title={t("palette.open")}>
				<ITStack spacing={3}>
					<ITInput
						name="palette"
						label={t("palette.placeholder")}
						autoFocus
						value={text}
						iconLeft={<FaSearch aria-hidden />}
						onChange={(event) => {
							setText(String(event.target.value));
							setSelected(0);
						}}
					/>
					<ITStack spacing={1} className="sf:max-h-96 sf:overflow-y-auto">
						{results.length === 0 && <ITText muted>{t("palette.empty")}</ITText>}
						{results.map((result, index) => (
							<ITButton
								key={result.id}
								variant={index === selected ? "filled" : "text"}
								data-palette-selected={index === selected}
								className="sf:w-full sf:justify-start sf:font-normal"
								onClick={() => go(result)}
							>
								<ITFlex align="center" gap={3} className="sf:w-full">
									{result.icon}
									<ITText as="span" className="sf:min-w-0 sf:flex-1 sf:truncate sf:text-left">{result.label}</ITText>
									<ITText as="span" className="sf:text-label-sm">{result.hint}</ITText>
								</ITFlex>
							</ITButton>
						))}
					</ITStack>
				</ITStack>
			</ITDialog>
		</>
	);
}
