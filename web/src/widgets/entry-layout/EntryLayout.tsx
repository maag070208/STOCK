import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

// The entry screen of the design: two equal halves, a white brand panel with the mark and the wordmark,
// and an orange panel with a white card. It is the brand moment, so it keeps this light look in dark mode.
export function EntryLayout({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	return (
		<main data-light className="sf:grid sf:min-h-screen sf:grid-cols-1 sf:lg:grid-cols-2">
			<section
				data-testid="login-brand-panel"
				className="sf:flex sf:flex-col sf:items-center sf:justify-center sf:gap-6 sf:bg-surface-elevated sf:p-8 sf:text-on-surface"
			>
				<img src="/brand/stockflow-glyph-light.svg" alt="" className="sf:w-40 sf:xl:w-64" />
				<h1 className="sf:text-headline-xl sf:xl:text-display-hero">{t("app.name")}</h1>
			</section>
			<section
				data-testid="login-action-panel"
				className="sf:flex sf:items-center sf:justify-center sf:bg-primary-strong sf:p-8 sf:text-on-primary"
			>
				<div
					data-testid="login-card"
					className="sf:flex sf:w-full sf:max-w-md sf:flex-col sf:gap-6 sf:rounded-md sf:bg-surface-elevated sf:p-8 sf:text-on-surface sf:shadow-dialog"
				>
					{children}
				</div>
			</section>
		</main>
	);
}
