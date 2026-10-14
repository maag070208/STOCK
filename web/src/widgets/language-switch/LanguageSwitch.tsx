import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { authApi, useSessionUser, userLoaded } from "@entities/user";
import { setLocale } from "@shared/i18n";
import { supportedLocales, type Locale } from "@shared/i18n/resources";

// A surface input, as the design calls it: neutral border, clean rectangle, orange focus ring.
// Signed in, the choice is saved on the person's profile (web.md section 6).
export function LanguageSwitch({ compact = false }: { compact?: boolean }) {
	const { t, i18n } = useTranslation();
	const user = useSessionUser();
	const dispatch = useDispatch();
	const id = compact ? "language-switch-top" : "language-switch";

	async function change(locale: Locale) {
		setLocale(locale);
		if (user) dispatch(userLoaded(await authApi.updateMe(locale)));
	}

	return (
		<div className={compact ? "sf:flex sf:items-center sf:gap-2" : "sf:flex sf:flex-col sf:gap-2"}>
			<label htmlFor={id} className={compact ? "sf:sr-only" : "sf:text-label-md sf:text-on-surface"}>
				{t("language.label")}
			</label>
			<select
				id={id}
				value={i18n.language}
				onChange={(event) => void change(event.target.value as Locale)}
				className={
					compact
						? "sf:h-9 sf:rounded-sm sf:border sf:border-shell-border/20 sf:bg-transparent sf:px-2 sf:text-body-md sf:text-on-shell sf:scheme-dark sf:focus:outline-2 sf:focus:outline-primary-focus"
						: "sf:h-9 sf:rounded-sm sf:border sf:border-outline-strong sf:bg-surface-elevated sf:px-3 sf:text-body-md sf:text-on-surface sf:focus:outline-2 sf:focus:outline-primary-focus"
				}
			>
				{supportedLocales.map((locale) => (
					<option key={locale} value={locale} className="sf:text-on-surface">
						{t(`language.${locale}`)}
					</option>
				))}
			</select>
		</div>
	);
}
