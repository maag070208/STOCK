import { type Column, ITDataTable, ITPage } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { auditApi, type AuditRecord } from "@entities/audit";
import type { TableParams } from "@shared/api/table";
import { dyn } from "@shared/i18n";
import { formatDateTime } from "@shared/lib/format";

const TARGETS = ["user", "role", "policy", "catalog", "catalog_item", "access"];

// What changed, who changed it and when; every denied access too. Read-only: nobody can change or delete a record.
export function AuditPage() {
	const { t, i18n } = useTranslation(["audit", "common"]);
	const actions = Object.keys(i18n.getResourceBundle("es", "audit")?.actions ?? {});

	const detail = (record: AuditRecord) => {
		const describe = (value: unknown) => (value && typeof value === "object" ? Object.entries(value as Record<string, unknown>).map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join(", ") : String(v)}`).join("; ") : "");
		const before = describe(record.before);
		const after = describe(record.after);
		return before && after ? `${before} → ${after}` : after || before;
	};

	const columns: Column<AuditRecord>[] = [
		{ key: "occurredAt", label: t("columns.occurredAt"), type: "date", filter: "date-range", sortable: true, render: (r) => formatDateTime(r.occurredAt, i18n.language) },
		{ key: "actor", label: t("columns.actor"), type: "string", filter: "search", sortable: true },
		{
			key: "action",
			label: t("columns.action"),
			type: "catalog",
			filter: "catalog",
			sortable: true,
			catalogOptions: { data: actions.map((action) => ({ id: action, name: t(dyn(`actions.${action}`)) })) },
			render: (r) => t(dyn(`actions.${r.action}`), { defaultValue: r.action }),
		},
		{
			key: "targetType",
			label: t("columns.target"),
			type: "catalog",
			filter: "catalog",
			sortable: true,
			catalogOptions: { data: TARGETS.map((target) => ({ id: target, name: t(dyn(`targets.${target}`)) })) },
			render: (r) => t(dyn(`targets.${r.targetType}`), { defaultValue: r.targetType }),
		},
		{ key: "targetId", label: "ID", type: "string", filter: "search", sortable: true, truncate: true },
		{ key: "detail", label: t("columns.detail"), type: "string", render: detail, truncate: true },
	];

	return (
		<ITPage title={t("title")} description={t("description")} noPadding>
			<ITDataTable<AuditRecord>
				defaultItemsPerPage={25}
				itemsPerPageOptions={[25, 50, 100]}
				layout="fixed"
				density="compact"
				virtualized
				virtualizedMaxHeight={400}
				rowHeight={50}
				showViewToggle={false} columns={columns} fetchData={(params) => auditApi.query(params as TableParams)}
			/>
		</ITPage>
	);
}
