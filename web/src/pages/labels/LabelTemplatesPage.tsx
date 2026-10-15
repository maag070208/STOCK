import { type Column, ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { labelTemplateApi, type LabelTemplate } from "@entities/label";
import { useCan } from "@entities/user";
import { formatDateTime } from "@shared/lib/format";
import { PlainTable } from "@shared/ui/PlainTable";

// The label templates: one per label type, each with its active version (labels.md section 2). The screen is built with
// the components of the UI kit only (web.md section 8).
export function LabelTemplatesPage() {
	const { t, i18n } = useTranslation(["labels", "common"]);
	const navigate = useNavigate();
	const canManage = useCan("labels.manage");
	const [templates, setTemplates] = useState<LabelTemplate[] | null>(null);

	useEffect(() => {
		void labelTemplateApi
			.list()
			.then(setTemplates)
			.catch(() => setTemplates([]));
	}, [i18n.language]);

	const columns: Column<LabelTemplate>[] = [
		{ key: "key", label: t("templates.columns.key"), type: "string" },
		{ key: "name", label: t("templates.columns.name"), type: "string" },
		{ key: "version", label: t("templates.columns.version"), type: "string" },
		{ key: "updatedAt", label: t("templates.columns.updatedAt"), type: "string", render: (template) => formatDateTime(template.updatedAt, i18n.language) },
		{
			key: "active",
			label: t("templates.columns.status"),
			type: "string",
			render: (template) => <ITBadget color={template.active ? "success" : "gray"} label={template.active ? t("common:status.active") : t("common:status.inactive")} />,
		},
	];

	return (
		<ITPage breadcrumbs={trail(t("common:nav.home"), navigate, { label: t("common:nav.labels"), onClick: () => navigate("/labels") }, { label: t("templates.title") })}
			title={t("templates.title")}
			description={t("templates.description")}
			noPadding
			loading={templates === null}
			actions={canManage ? <ITButton label={t("templates.new")} onClick={() => navigate("/labels/new")} /> : undefined}
		>
			<PlainTable<LabelTemplate>columns={columns} data={templates ?? []} size="sm" onRowClick={(template) => navigate(`/labels/${template.id}`)} />
		</ITPage>
	);
}
