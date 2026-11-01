import { useITTheme } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { FaMoon, FaSun } from "react-icons/fa";
import { IconButton } from "@shared/ui/IconButton";

// The light/dark switch of the system, in the top bar. The kit keeps the choice in localStorage and the whole app follows
// it; the design is light-first, but every surface exists in both modes.
export function ThemeSwitch() {
	const { resolvedTheme, setDarkModeMode } = useITTheme();
	const { t } = useTranslation();
	const dark = resolvedTheme === "dark";
	return (
		<IconButton
			icon={dark ? <FaSun aria-hidden /> : <FaMoon aria-hidden />}
			label={dark ? t("actions.lightMode") : t("actions.darkMode")}
			variant="text"
			onClick={() => setDarkModeMode(dark ? "light" : "dark")}
		/>
	);
}
