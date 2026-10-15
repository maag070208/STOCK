import { type Column, ITBadget, ITButton, ITConfirmDialog, ITDataTable, ITDropdownMenu, type ITDropdownMenuItem, ITFlex, ITPage } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaEllipsisV } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { productApi, type Product } from "@entities/product";
import { useCan } from "@entities/user";
import { ProductDialog } from "@features/product/ProductDialog";
import type { TableParams } from "@shared/api/table";
import { ApiError } from "@shared/api/client";
import { toast } from "@app/store";

type Confirm = { kind: "deactivate" | "reactivate"; product: Product };

// The materials list. Who can read sees it; who can manage creates, edits, deactivates, and imports the legacy file.
export function ProductsPage() {
	const { t } = useTranslation(["products", "common"]);
	const navigate = useNavigate();
	const canManage = useCan("products.manage");
	const [units, setUnits] = useState<CatalogItem[]>([]);
	const [reload, setReload] = useState(0);
	const [editing, setEditing] = useState<{ product: Product | null } | null>(null);
	const [confirm, setConfirm] = useState<Confirm | null>(null);
	const refresh = () => setReload((value) => value + 1);

	// The units name the filter and the form. Every reader of materials can read catalogs.
	useEffect(() => {
		void catalogApi.items("UNIT_OF_MEASURE").then(setUnits).catch(() => setUnits([]));
	}, []);

	const columns: Column<Product>[] = [
		{ key: "code", label: t("columns.code"), type: "string", filter: "search", sortable: true },
		{ key: "name", label: t("columns.name"), type: "string", filter: "search", sortable: true },
		{ key: "barcode", label: t("columns.barcode"), type: "string", filter: "search", sortable: true },
		{
			key: "unit",
			label: t("columns.unit"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: units.map((unit) => ({ id: unit.code, name: unit.label ?? unit.code })) },
			render: (product) => product.unit.label ?? product.unit.code,
		},
		{
			key: "active",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: [{ id: "true", name: t("common:status.active") }, { id: "false", name: t("common:status.inactive") }] },
			render: (product) => <ITBadget color={product.active ? "success" : "gray"} label={t(product.active ? "common:status.active" : "common:status.inactive")} />,
		},
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (product) => {
				const items: ITDropdownMenuItem[] = [{ id: "open", label: t("actions.open"), onClick: () => navigate(`/products/${product.id}`) }];
				if (canManage) {
					items.push({ id: "edit", label: t("actions.edit"), onClick: () => setEditing({ product }) });
					if (product.active) items.push({ id: "deactivate", label: t("actions.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivate", product }) });
					else items.push({ id: "reactivate", label: t("actions.reactivate"), onClick: () => setConfirm({ kind: "reactivate", product }) });
				}
				return <ITDropdownMenu items={items} trigger={<FaEllipsisV aria-hidden />} triggerLabel={t("columns.actions")} placement="bottom-end" />;
			},
		},
	];

	async function confirmed() {
		if (!confirm) return;
		const { kind, product } = confirm;
		setConfirm(null);
		try {
			await (kind === "deactivate" ? productApi.deactivate(product.id) : productApi.reactivate(product.id));
			toast(t("common:saved"));
			refresh();
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
		}
	}

	return (
		<ITPage
			title={t("title")}
			description={t("description")}
			noPadding
			actions={
				canManage ? (
					<ITFlex gap={2}>
						<ITButton variant="outlined" label={t("import")} onClick={() => navigate("/products/import")} />
						<ITButton label={t("new")} onClick={() => setEditing({ product: null })} disabled={units.length === 0} />
					</ITFlex>
				) : undefined
			}
		>
			<ITDataTable<Product>
				showViewToggle={false}
				columns={columns}
				fetchData={(params) => productApi.query(params as TableParams)}
				reloadTrigger={reload}
				layout="fixed"
				onRowClick={(product) => navigate(`/products/${product.id}`)}
			/>
			<ProductDialog
				isOpen={!!editing}
				product={editing?.product ?? null}
				units={units}
				onClose={() => setEditing(null)}
				onSaved={() => {
					setEditing(null);
					toast(t("common:saved"));
					refresh();
				}}
			/>
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(`actions.${confirm.kind}`) : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { name: confirm.product.name }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivate" ? "danger" : "primary"}
			/>
		</ITPage>
	);
}
