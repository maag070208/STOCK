import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import type { ReportKind } from "@entities/report";
import { PillTabs } from "@shared/ui/PillTabs";

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
	const current = TABS.find((tab) => tab.path === pathname)?.id ?? "stock";
	return (
		<PillTabs
			label={t("tabs.label")}
			current={current}
			tabs={TABS.map((tab) => ({ id: tab.id, label: t(`tabs.${tab.id}`) }))}
			onSelect={(id) => navigate(TABS.find((tab) => tab.id === id)?.path ?? "/reports/stock")}
		/>
	);
}
