import { ITBadget, ITButton, ITFlex, ITPage, ITText } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { productApi, type Product } from "@entities/product";
import { useCan } from "@entities/user";
import { PrintDialog, type PrintTarget } from "@features/label/PrintDialog";
import { ProductDialog } from "@features/product/ProductDialog";
import { formatDateTime, formatQuantity } from "@shared/lib/format";
import { toast } from "@app/store";
import { FactStrip } from "@shared/ui/FactStrip";
import { stockApi, type ProductTotal } from "@entities/stock";

// One material: its fields, editing for who can manage, and its label for who prints. The way back is the breadcrumbs,
// and the detail is the kit's grid and cards (web.md section 8).
export function ProductPage() {
	const { t, i18n } = useTranslation(["products", "common", "labels"]);
	const { id = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useCan("products.manage");
	const canPrint = useCan("labels.print");
	const [product, setProduct] = useState<Product | null>(null);
	const [missing, setMissing] = useState(false);
	const [units, setUnits] = useState<CatalogItem[]>([]);
	const [editing, setEditing] = useState(false);
	const [printing, setPrinting] = useState<PrintTarget | null>(null);

	const canStock = useCan("stock.view");
	const [stock, setStock] = useState<ProductTotal[] | null>(null);
	const load = useCallback(async () => {
		try {
			const found = await productApi.get(id);
			setProduct(found);
			// The total of the material in the warehouses the reader reaches. Without the permission the fact is not there.
			if (canStock) void stockApi.products({ page: 1, limit: 200, filters: { product: found.code } }).then((page) => setStock(page.data.filter((row) => row.productId === found.id))).catch(() => setStock(null));
		} catch {
			setMissing(true);
		}
	}, [id, canStock]);

	useEffect(() => {
		void load();
		void catalogApi.items("UNIT_OF_MEASURE").then(setUnits).catch(() => setUnits([]));
	}, [load]);

	const breadcrumbs = [{ label: t("title"), onClick: () => navigate("/products") }, { label: product ? `${product.code} · ${product.name}` : t("title") }];

	if (missing) {
		return (
			<ITPage title={t("title")} breadcrumbs={breadcrumbs} noPadding>
				<ITText>{t("common:errors.notFound")}</ITText>
			</ITPage>
		);
	}

	const stockOf = stock ? formatQuantity(stock.reduce((sum, row) => sum + Number(row.quantity), 0), product?.unit.decimalPlaces, i18n.language) : null;
	const fields: { label: string; value: string }[] = product
		? [
				...(stockOf !== null ? [{ label: t("common:stats.stock"), value: `${stockOf} ${product.unit.label ?? product.unit.code}` }] : []),
				{ label: t("columns.code"), value: product.code },
				{ label: t("columns.name"), value: product.name },
				{ label: t("columns.barcode"), value: product.barcode },
				{ label: t("columns.unit"), value: product.unit.label ?? product.unit.code },
				{ label: t("detail.legacyId"), value: product.legacyId === null || product.legacyId === undefined ? t("detail.none") : String(product.legacyId) },
				{ label: t("detail.createdAt"), value: formatDateTime(product.createdAt, i18n.language) },
			]
		: [];

	return (
		<ITPage
			title={product?.code ?? t("title")}
			description={product?.name ?? undefined}
			breadcrumbs={breadcrumbs}
			noPadding
			loading={!product}
			actions={
				product ? (
					<ITFlex align="center" gap={2}>
						<ITBadget color={product.active ? "success" : "gray"} label={t(product.active ? "common:status.active" : "common:status.inactive")} />
						{canPrint && product.active && (
							<ITButton variant="outlined" label={t("labels:print.button")} onClick={() => setPrinting({ kind: "PRODUCT", productId: product.id, title: `${product.code} · ${product.name}` })} />
						)}
						{canManage && <ITButton label={t("actions.edit")} onClick={() => setEditing(true)} disabled={units.length === 0} />}
					</ITFlex>
				) : undefined
			}
		>
			<FactStrip hero={stockOf !== null} perRow={3} facts={fields.map((field) => ({ label: field.label, value: field.value }))} />
			{product && (
				<ProductDialog
					isOpen={editing}
					product={product}
					units={units}
					onClose={() => setEditing(false)}
					onSaved={(saved) => {
						setEditing(false);
						setProduct(saved);
						toast(t("common:saved"));
					}}
				/>
			)}
			<PrintDialog target={printing} onClose={() => setPrinting(null)} />
		</ITPage>
	);
}
