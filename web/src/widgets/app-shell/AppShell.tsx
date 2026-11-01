import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import { ITLayout, useITTheme, type ITNavigationItem } from "@axzydev/axzy_ui_system";
import { FaChartBar, FaClipboardList, FaCubes, FaHome, FaHandshake, FaPrint, FaUserCog } from "react-icons/fa";
import { APP_SCREENS, isScreenVisible, NAV_GROUPS, type AppScreen, type NavGroupId } from "@entities/permission";
import { authApi, signedOut, usePermissions, useSessionUser } from "@entities/user";
import { CommandPalette } from "@widgets/command-palette/CommandPalette";
import { LanguageSwitch } from "@widgets/language-switch/LanguageSwitch";
import { ThemeSwitch } from "@widgets/theme-switch/ThemeSwitch";

const COLLAPSED_KEY = "stockflow.menu-collapsed";

const SCREEN_ICONS: Record<string, ReactNode> = {
	home: <FaHome />,
	reports: <FaChartBar />,
};

const GROUP_ICONS: Record<NavGroupId, ReactNode> = {
	operation: <FaClipboardList />,
	inventory: <FaCubes />,
	partners: <FaHandshake />,
	printing: <FaPrint />,
	admin: <FaUserCog />,
};

// The private shell: the bento layout of the kit, a white floating sidebar and top bar around the canvas. The look is
// the provider's (`appearance="bento"` in main.tsx); the shell only says what is inside.
// The menu is built from APP_SCREENS and the person's permissions, section by section; the API still checks every request.
export function AppShell({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	const navigate = useNavigate();
	const location = useLocation();
	const dispatch = useDispatch();
	const user = useSessionUser();
	const permissions = usePermissions();
	// The sidebar and the top bar are white cards in light mode and dark ones in dark mode, and the mark follows them.
	const glyph = useITTheme().resolvedTheme === "dark" ? "/brand/stockflow-glyph-dark.svg" : "/brand/stockflow-glyph-light.svg";
	// The menu stays open: its names are the map of the system, and the person chooses when to fold it (top bar).
	const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSED_KEY) === "1");
	const toggleMenu = () => {
		localStorage.setItem(COLLAPSED_KEY, collapsed ? "0" : "1");
		setCollapsed(!collapsed);
	};
	const refreshToken = useSelector((state: { auth: { refreshToken: string | null } }) => state.auth.refreshToken);

	const active = (screen: AppScreen) =>
		screen.match === "exact" ? location.pathname === screen.path : location.pathname === screen.path || location.pathname.startsWith(`${screen.path}/`);

	const visible = APP_SCREENS.filter((screen) => isScreenVisible(permissions, screen));
	const groups = new Map(NAV_GROUPS.map((group) => [group.id, visible.filter((screen) => screen.group === group.id)]));
	const placed = new Set<NavGroupId>();

	// Each screen of a section brings the section to its first place. A section with one screen is that screen: nobody
	// opens a menu to reach the only thing inside it.
	const navigationItems: ITNavigationItem[] = visible.flatMap<ITNavigationItem>((screen) => {
		const members = screen.group ? groups.get(screen.group)! : [screen];
		if (screen.group && placed.has(screen.group)) return [];
		if (screen.group) placed.add(screen.group);
		if (members.length === 1) {
			const icon = SCREEN_ICONS[screen.id] ?? (screen.group ? GROUP_ICONS[screen.group] : undefined);
			return [{ id: screen.id, label: t(screen.labelKey), icon, action: () => navigate(screen.path), isActive: active(screen) }];
		}
		const group = NAV_GROUPS.find((candidate) => candidate.id === screen.group)!;
		return [
			{
				id: group.id,
				label: t(group.labelKey),
				icon: GROUP_ICONS[group.id],
				isActive: members.some(active),
				subitems: members.map((member) => ({ id: member.id, label: t(member.labelKey), action: () => navigate(member.path), isActive: active(member) })),
			},
		];
	});

	// The refresh token is revoked first, while the session can still call the API; then the session ends here.
	async function signOut() {
		await authApi.logout(refreshToken).catch(() => undefined);
		dispatch(signedOut());
		navigate("/login", { replace: true });
	}

	return (
		<ITLayout
			className="sf-shell"
			contentClassName="sf:max-w-(--sf-layout-container-max) sf:h-auto sf:min-h-full sf:pb-12"
			sidebarFullHeight
			topBar={{
				variant: "bento",
				logo: <img src={glyph} alt="" className="sf:size-9" />,
				logoText: t("app.name"),
				centerContent: <CommandPalette />,
				children: (
					<>
						<ThemeSwitch />
						<LanguageSwitch compact />
					</>
				),
				userMenu: user
					? {
							userName: user.name,
							userEmail: user.username,
							menuItems: [{ label: t("actions.signOut"), onClick: () => void signOut() }],
						}
					: undefined,
			}}
			sidebar={{
				variant: "bento",
				navigationItems,
				profile: user ? { name: user.name, detail: user.username } : undefined,
				brand: { text: t("app.name"), logo: <img src={glyph} alt="" className="sf:size-10" /> },
				isCollapsed: collapsed,
				onToggleCollapse: toggleMenu,
				expandOnHover: false,
			}}
		>
			{children}
		</ITLayout>
	);
}
