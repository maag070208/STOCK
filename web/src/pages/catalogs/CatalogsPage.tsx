import { ITBadget, ITButton, ITInput, ITPage, ITSelect } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { catalogApi, type Catalog, type CatalogItem } from "@entities/catalog";
import { useCan } from "@entities/user";
import { CatalogItemDialog } from "@features/catalog/CatalogItemDialog";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { toast } from "@app/store";

// The value catalogs: statuses, units and the ones an administrator adds. Codes never change; values are deactivated, not deleted.
export function CatalogsPage() {
	const { t } = useTranslation(["catalogs", "common"]);
	const canManage = useCan("catalogs.manage");
	const [catalogs, setCatalogs] = useState<Catalog[]>([]);
	const [selected, setSelected] = useState("");
	const [items, setItems] = useState<CatalogItem[]>([]);
	const [showInactive, setShowInactive] = useState(false);
	const [editing, setEditing] = useState<{ item: CatalogItem | null } | null>(null);
	const [creatingCatalog, setCreatingCatalog] = useState(false);
	const [newCode, setNewCode] = useState("");
	const submit = useSubmit();

	const loadCatalogs = useCallback(async (select?: string) => {
		const list = await catalogApi.list();
		setCatalogs(list);
		setSelected((current) => select ?? (current || list[0]?.code || ""));
	}, []);

	const loadItems = useCallback(async () => {
		if (selected) setItems(await catalogApi.items(selected, showInactive));
	}, [selected, showInactive]);

	useEffect(() => {
		void loadCatalogs();
	}, [loadCatalogs]);
	useEffect(() => {
		void loadItems();
	}, [loadItems]);

	async function createCatalog() {
		const created = await submit.run(() => catalogApi.create(newCode));
		if (created) {
			setCreatingCatalog(false);
			setNewCode("");
			toast(t("common:saved"));
			await loadCatalogs(created.code);
		}
	}

	const attributes = (item: CatalogItem) =>
		[item.attributes.terminal === true ? t("attributes.terminal") : null, item.attributes.decimal_places !== undefined ? t("attributes.decimal_places", { value: String(item.attributes.decimal_places) }) : null]
			.filter(Boolean)
			.join(" · ");

	return (
		<ITPage
			title={t("title")}
			description={t("description")}
			noPadding
			actions={
				canManage ? (
					<div className="sf:flex sf:gap-2">
						<ITButton variant="outlined" label={t("new")} onClick={() => { submit.reset(); setCreatingCatalog(true); }} />
						<ITButton label={t("newItem")} onClick={() => setEditing({ item: null })} disabled={!selected} />
					</div>
				) : undefined
			}
		>
			<div className="sf:flex sf:flex-col sf:gap-4">
				<div className="sf:flex sf:flex-wrap sf:items-end sf:gap-4">
					<div className="sf:w-72">
						<ITSelect
							name="catalog"
							label={t("catalog")}
							value={selected}
							onChange={(e) => setSelected(e.target.value)}
							options={catalogs.map((c) => ({ value: c.code, label: c.isSystem ? `${c.code} · ${t("system")}` : c.code }))}
							valueField="value"
							labelField="label"
						/>
					</div>
					<label className="sf:flex sf:items-center sf:gap-2 sf:pb-2 sf:text-body-md">
						<input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
						{t("showInactive")}
					</label>
				</div>
				<table className="sf:w-full sf:text-body-md" data-testid="catalog-items">
					<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
						<tr>
							<th className="sf:p-3 sf:text-right">{t("columns.sortOrder")}</th>
							<th className="sf:p-3 sf:text-left">{t("columns.code")}</th>
							<th className="sf:p-3 sf:text-left">{t("columns.labelEs")}</th>
							<th className="sf:p-3 sf:text-left">{t("columns.labelEn")}</th>
							<th className="sf:p-3 sf:text-left">{t("columns.attributes")}</th>
							<th className="sf:p-3 sf:text-left">{t("columns.status")}</th>
							<th className="sf:p-3" />
						</tr>
					</thead>
					<tbody>
						{items.map((item) => (
							<tr key={item.code} className="sf:border-t sf:border-outline" data-item={item.code}>
								<td className="sf:p-3 sf:text-right">{item.sortOrder}</td>
								<td className="sf:p-3 sf:text-code-sm">{item.code}</td>
								<td className="sf:p-3">{item.labels.es?.label}</td>
								<td className="sf:p-3">{item.labels.en?.label}</td>
								<td className="sf:p-3 sf:text-on-surface-muted">{attributes(item)}</td>
								<td className="sf:p-3">
									<ITBadget color={item.active ? "success" : "gray"} label={item.active ? t("common:status.active") : t("common:status.inactive")} />
								</td>
								<td className="sf:p-3 sf:text-right">{canManage && <ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => setEditing({ item })} />}</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>

			<CatalogItemDialog
				catalog={selected}
				item={editing?.item ?? null}
				isOpen={!!editing}
				onClose={() => setEditing(null)}
				onSaved={() => {
					setEditing(null);
					toast(t("common:saved"));
					void loadItems();
					void loadCatalogs(selected);
				}}
			/>
			<FormDialog isOpen={creatingCatalog} title={t("new")} onClose={() => setCreatingCatalog(false)} onSubmit={() => void createCatalog()} submitting={submit.submitting} error={submit.error} submitLabel={t("common:actions.create")}>
				<ITInput name="code" label={t("form.code")} value={newCode} onChange={(e) => setNewCode(e.target.value.toUpperCase())} error={submit.fieldErrors.code ?? t("form.codeHint")} touched={!!submit.fieldErrors.code} required />
			</FormDialog>
		</ITPage>
	);
}
