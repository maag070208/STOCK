import { ITButton } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import type { ReportKind } from "@entities/report";

// The five reports, each with its own address so a link can point at one of them (reports.md section 3).
const TABS: { id: ReportKind; path: string }[] = [
	{ id: "stock", path: "/reports/stock" },
	{ id: "kardex", path: "/reports/kardex" },
	{ id: "movements", path: "/reports/movements" },
	{ id: "orders", path: "/reports/orders" },
	{ id: "invoices", path: "/reports/invoices" },
];

export function ReportTabs() {
	const { t } = useTranslation("reports");
	const navigate = useNavigate();
	const { pathname } = useLocation();
	return (
		<nav aria-label={t("tabs.label")} className="sf:mb-4 sf:flex sf:flex-wrap sf:gap-2">
			{TABS.map((tab) => (
				<ITButton
					key={tab.id}
					size="sm"
					variant={pathname === tab.path ? undefined : "outlined"}
					label={t(`tabs.${tab.id}`)}
					onClick={() => navigate(tab.path)}
				/>
			))}
		</nav>
	);
}
