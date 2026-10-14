import { ITButton, ITEmptyState } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

// The 403 page: the screen exists, and this person may not see it.
export function ForbiddenPage() {
	const { t } = useTranslation();
	const navigate = useNavigate();
	return (
		<div data-testid="forbidden" className="sf:py-16">
			<ITEmptyState title={t("forbidden.title")} description={t("forbidden.description")} action={<ITButton label={t("forbidden.back")} onClick={() => navigate("/")} />} />
		</div>
	);
}
