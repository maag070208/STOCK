import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import { ITLayout, type ITNavigationItem } from "@axzydev/axzy_ui_system";
import { FaBalanceScale, FaBoxes, FaClipboardList, FaCubes, FaFileInvoice, FaHandshake, FaHistory, FaHome, FaListUl, FaPrint, FaTags, FaTruck, FaUserShield, FaUsers, FaWarehouse } from "react-icons/fa";
import { APP_SCREENS, isScreenVisible, type AppScreen } from "@entities/permission";
import { authApi, signedOut, usePermissions, useSessionUser } from "@entities/user";
import { LanguageSwitch } from "@widgets/language-switch/LanguageSwitch";

const ICONS: Record<string, ReactNode> = {
	home: <FaHome />,
	warehouses: <FaWarehouse />,
	products: <FaBoxes />,
	stock: <FaCubes />,
	invoices: <FaFileInvoice />,
	discrepancies: <FaBalanceScale />,
	suppliers: <FaTruck />,
	customers: <FaHandshake />,
	labels: <FaTags />,
	printers: <FaPrint />,
	users: <FaUsers />,
	roles: <FaUserShield />,
	catalogs: <FaListUl />,
	audit: <FaHistory />,
};

// The private shell: dark glass sidebar and top bar around the light workspace. Its look is set in app/index.css.
// The menu is built from APP_SCREENS and the person's permissions; the API still checks every request.
export function AppShell({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	const navigate = useNavigate();
	const location = useLocation();
	const dispatch = useDispatch();
	const user = useSessionUser();
	const permissions = usePermissions();
	const refreshToken = useSelector((state: { auth: { refreshToken: string | null } }) => state.auth.refreshToken);
	// The sidebar starts open and the top bar's menu button collapses it. The kit's default is a rail that opens on hover.
	const [collapsed, setCollapsed] = useState(false);

	const active = (screen: AppScreen) =>
		screen.match === "exact" ? location.pathname === screen.path : location.pathname === screen.path || location.pathname.startsWith(`${screen.path}/`);

	const navigationItems: ITNavigationItem[] = APP_SCREENS.filter((screen) => isScreenVisible(permissions, screen)).map((screen) => ({
		id: screen.id,
		label: t(screen.labelKey),
		icon: ICONS[screen.id] ?? <FaClipboardList />,
		action: () => navigate(screen.path),
		isActive: active(screen),
	}));

	// The refresh token is revoked first, while the session can still call the API; then the session ends here.
	async function signOut() {
		await authApi.logout(refreshToken).catch(() => undefined);
		dispatch(signedOut());
		navigate("/login", { replace: true });
	}

	return (
		<ITLayout
			className="sf-shell"
			contentClassName="sf:max-w-(--sf-layout-container-max)"
			sidebarFullHeight
			topBar={{
				logoText: t("app.name"),
				children: <LanguageSwitch compact />,
				userMenu: user
					? {
							userName: user.name,
							userEmail: user.username,
							menuItems: [{ label: t("actions.signOut"), onClick: () => void signOut() }],
						}
					: undefined,
			}}
			sidebar={{
				navigationItems,
				// The shell is dark in both modes, so it always carries the glyph drawn for dark backgrounds.
				brand: { text: t("app.name"), logo: <img src="/brand/stockflow-glyph-dark.svg" alt="" className="sf:size-10" /> },
				expandOnHover: false,
				isCollapsed: collapsed,
				onToggleCollapse: () => setCollapsed((value) => !value),
			}}
		>
			{children}
		</ITLayout>
	);
}
