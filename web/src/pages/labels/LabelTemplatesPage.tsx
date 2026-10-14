import { ITBadget, ITButton, ITPage } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { labelTemplateApi, type LabelTemplate } from "@entities/label";
import { useCan } from "@entities/user";
import { formatDateTime } from "@shared/lib/format";

// The label templates: one per label type, each with its active version (labels.md section 2).
export function LabelTemplatesPage() {
	const { t, i18n } = useTranslation(["labels", "common"]);
	const navigate = useNavigate();
	const canManage = useCan("labels.manage");
	const [templates, setTemplates] = useState<LabelTemplate[] | null>(null);

	useEffect(() => {
		void labelTemplateApi.list().then(setTemplates).catch(() => setTemplates([]));
	}, [i18n.language]);

	return (
		<ITPage
			title={t("templates.title")}
			description={t("templates.description")}
			noPadding
			loading={templates === null}
			actions={canManage ? <ITButton label={t("templates.new")} onClick={() => navigate("/labels/new")} /> : undefined}
		>
			<table className="sf:w-full sf:text-body-md" data-testid="label-templates">
				<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
					<tr>
						<th className="sf:p-3 sf:text-left">{t("templates.columns.key")}</th>
						<th className="sf:p-3 sf:text-left">{t("templates.columns.name")}</th>
						<th className="sf:p-3 sf:text-right">{t("templates.columns.version")}</th>
						<th className="sf:p-3 sf:text-left">{t("templates.columns.updatedAt")}</th>
						<th className="sf:p-3 sf:text-left">{t("templates.columns.status")}</th>
					</tr>
				</thead>
				<tbody>
					{(templates ?? []).map((template) => (
						<tr
							key={template.id}
							className="sf:cursor-pointer sf:border-t sf:border-outline sf:hover:bg-surface"
							data-template={template.key}
							onClick={() => navigate(`/labels/${template.id}`)}
						>
							<td className="sf:p-3 sf:text-code-sm">{template.key}</td>
							<td className="sf:p-3">{template.name}</td>
							<td className="sf:p-3 sf:text-right">{template.version}</td>
							<td className="sf:p-3 sf:text-on-surface-muted">{formatDateTime(template.updatedAt, i18n.language)}</td>
							<td className="sf:p-3">
								<ITBadget color={template.active ? "success" : "gray"} label={template.active ? t("common:status.active") : t("common:status.inactive")} />
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</ITPage>
	);
}
