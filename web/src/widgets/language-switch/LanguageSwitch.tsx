import { ITSelect } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { authApi, useSessionUser, userLoaded } from "@entities/user";
import { setLocale } from "@shared/i18n";
import { supportedLocales, type Locale } from "@shared/i18n/resources";

// The language field. On the entry screen it is the kit's select; the top bar keeps a compact
// control drawn on the dark glass of the shell.
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

	if (compact) {
		return (
			<div className="sf:flex sf:items-center sf:gap-2">
				<label htmlFor={id} className="sf:sr-only">
					{t("language.label")}
				</label>
				<select
					id={id}
					value={i18n.language}
					onChange={(event) => void change(event.target.value as Locale)}
					className="sf:h-9 sf:rounded-sm sf:border sf:border-shell-border/20 sf:bg-transparent sf:px-2 sf:text-body-md sf:text-on-shell sf:scheme-dark sf:focus:outline-2 sf:focus:outline-primary-focus"
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

	return (
		<ITSelect
			name={id}
			label={t("language.label")}
			options={supportedLocales.map((locale) => ({ value: locale, label: t(`language.${locale}`) }))}
			value={i18n.language}
			onChange={(event) => void change(event.target.value as Locale)}
		/>
	);
}
