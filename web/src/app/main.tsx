import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ITThemeProvider } from "@axzydev/axzy_ui_system";
import { Provider } from "react-redux";
import "./index.css";
import "../shared/i18n";
import { themeSettings } from "../shared/ui/theme";
import { App } from "./App";
import { store } from "./store";

createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<ITThemeProvider showFab={false} appearance={themeSettings.appearance} shadow={themeSettings.shadow} theme={themeSettings.palette}>
			<Provider store={store}>
				<App />
			</Provider>
		</ITThemeProvider>
	</StrictMode>,
);
