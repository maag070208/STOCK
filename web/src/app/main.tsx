import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ITThemeProvider } from "@axzydev/axzy_ui_system";
import { Provider } from "react-redux";
import "./index.css";
import "../shared/i18n";
import { themeSettings } from "../shared/ui/theme";
import { App } from "./App";
import { store } from "./store";

// The design is light-first; the dark mode stays one setting away, as the kit's own control changes it.
// Without a stored choice the kit follows the system, which is not what the design asks for.
if (!localStorage.getItem("it-theme-dark-mode")) {
	localStorage.setItem("it-theme-dark-mode", "light");
}

// The appearance, the density and the palette come from the design tokens (docs/architecture/design-system.md).
// `shadow` and `radius` are left out on purpose: the bento appearance defaults are the design's (24px cards, a
// diffuse shadow, pill controls), and passing them would replace those defaults with the kit's plain ones.
createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<ITThemeProvider showFab={false} appearance={themeSettings.appearance} density={themeSettings.density} theme={themeSettings.palette}>
			<Provider store={store}>
				<App />
			</Provider>
		</ITThemeProvider>
	</StrictMode>,
);
