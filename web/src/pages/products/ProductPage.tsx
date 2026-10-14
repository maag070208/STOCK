import { ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { productApi, type Product } from "@entities/product";
import { useCan } from "@entities/user";
import { PrintDialog, type PrintTarget } from "@features/label/PrintDialog";
import { ProductDialog } from "@features/product/ProductDialog";
import { formatDateTime } from "@shared/lib/format";
import { toast } from "@app/store";

// One material: its fields, editing for who can manage, and its label for who prints.
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

	const load = useCallback(async () => {
		try {
			setProduct(await productApi.get(id));
		} catch {
			setMissing(true);
		}
	}, [id]);

	useEffect(() => {
		void load();
		void catalogApi.items("UNIT_OF_MEASURE").then(setUnits).catch(() => setUnits([]));
	}, [load]);

	const back = <ITButton variant="outlined" label={t("detail.back")} onClick={() => navigate("/products")} />;
	if (missing) {
		return (
			<ITPage title={t("title")} noPadding>
				<p className="sf:text-body-md">{t("common:errors.notFound")}</p>
				{back}
			</ITPage>
		);
	}

	const fields: [string, React.ReactNode][] = product
		? [
				[t("columns.code"), product.code],
				[t("columns.name"), product.name],
				[t("columns.barcode"), product.barcode],
				[t("columns.unit"), product.unit.label ?? product.unit.code],
				[t("detail.legacyId"), product.legacyId ?? t("detail.none")],
				[t("detail.createdAt"), formatDateTime(product.createdAt, i18n.language)],
				[t("columns.status"), <ITBadget color={product.active ? "success" : "gray"} label={t(product.active ? "common:status.active" : "common:status.inactive")} />],
			]
		: [];

	return (
		<ITPage
			title={product ? `${product.code} · ${product.name}` : t("title")}
			noPadding
			loading={!product}
			actions={
				<div className="sf:flex sf:gap-2">
					{back}
					{canPrint && product?.active && (
						<ITButton variant="outlined" label={t("labels:print.button")} onClick={() => setPrinting({ kind: "PRODUCT", productId: product.id, title: `${product.code} · ${product.name}` })} />
					)}
					{canManage && <ITButton label={t("actions.edit")} onClick={() => setEditing(true)} disabled={units.length === 0} />}
				</div>
			}
		>
			<dl className="sf:grid sf:max-w-2xl sf:grid-cols-[max-content_1fr] sf:gap-x-8 sf:gap-y-3" data-testid="product-detail">
				{fields.map(([label, value]) => (
					<div key={label} className="sf:contents">
						<dt className="sf:text-label-md sf:text-on-surface-muted">{label}</dt>
						<dd className="sf:text-body-md">{value}</dd>
					</div>
				))}
			</dl>
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
