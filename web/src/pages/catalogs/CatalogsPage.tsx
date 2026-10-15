import { ITBadget, ITButton, ITCheckbox, ITFlex, ITGrid, ITInput, ITPage, ITSearchSelect, ITStack, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { IconButton } from "@shared/ui/IconButton";
import { FaEdit } from "react-icons/fa";
import { catalogApi, type Catalog, type CatalogItem } from "@entities/catalog";
import { useCan } from "@entities/user";
import { CatalogItemDialog } from "@features/catalog/CatalogItemDialog";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { toast } from "@app/store";
import { PlainTable } from "@shared/ui/PlainTable";

// One row of the table: the catalog item, which the kit tells apart by its code.
type CatalogRow = Record<string, unknown> & { id: string; item: CatalogItem };

// The value catalogs: statuses, units and the ones an administrator adds. Codes never change; values are deactivated, not deleted.
// The selector, the table and the dialog are the kit's (web.md section 8).
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

	const rows: CatalogRow[] = items.map((item) => ({ id: item.code, item }));

	const columns: Column<CatalogRow>[] = [
		{ key: "sortOrder", label: t("columns.sortOrder"), type: "string", className: "sf:text-right", render: (row) => String(row.item.sortOrder) },
		{ key: "code", label: t("columns.code"), type: "string", render: (row) => <ITText className="sf:text-code-sm">{row.item.code}</ITText> },
		{ key: "labelEs", label: t("columns.labelEs"), type: "string", render: (row) => row.item.labels.es?.label ?? "" },
		{ key: "labelEn", label: t("columns.labelEn"), type: "string", render: (row) => row.item.labels.en?.label ?? "" },
		{ key: "attributes", label: t("columns.attributes"), type: "string", render: (row) => <ITText muted>{attributes(row.item)}</ITText> },
		{
			key: "status",
			label: t("columns.status"),
			type: "string",
			render: (row) => <ITBadget color={row.item.active ? "success" : "gray"} label={row.item.active ? t("common:status.active") : t("common:status.inactive")} />,
		},
		{
			key: "actions",
			label: "",
			type: "actions",
			className: "sf:text-right",
			actions: (row) => (canManage ? <IconButton icon={<FaEdit aria-hidden />} label={t("common:actions.edit")} onClick={() => setEditing({ item: row.item })} /> : null),
		},
	];

	return (
		<ITPage
			title={t("title")}
			description={t("description")}
			noPadding
			actions={
				canManage ? (
					<ITFlex gap={2}>
						<ITButton variant="outlined" label={t("new")} onClick={() => { submit.reset(); setCreatingCatalog(true); }} />
						<ITButton label={t("newItem")} onClick={() => setEditing({ item: null })} disabled={!selected} />
					</ITFlex>
				) : undefined
			}
		>
			<ITStack spacing={4}>
				<ITGrid container spacing={4}>
					<ITGrid item xs={12} sm={6} md={4}>
						<ITSearchSelect
							name="catalog"
							label={t("catalog")}
							value={selected}
							onChange={(value) => setSelected(String(value))}
							options={catalogs.map((c) => ({ value: c.code, label: c.isSystem ? `${c.code} · ${t("system")}` : c.code }))}
						/>
					</ITGrid>
					<ITGrid item xs={12} sm={6} md={4}>
						<ITCheckbox name="showInactive" label={t("showInactive")} checked={showInactive} onChange={setShowInactive} />
					</ITGrid>
				</ITGrid>
				<PlainTable<CatalogRow>
					columns={columns}
					data={rows}
					size="sm"
				/>
			</ITStack>

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
