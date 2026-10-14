import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { defaultLocale, namespaces, resources, supportedLocales, type Locale } from "./resources";

const storageKey = "stockflow.locale";

// The choice is kept per browser. Anything unknown falls back to the default locale.
function initialLocale(): Locale {
	try {
		const stored = window.localStorage.getItem(storageKey);
		if (stored && (supportedLocales as readonly string[]).includes(stored)) return stored as Locale;
	} catch {
		// Storage can be blocked; the default locale is used.
	}
	return defaultLocale;
}

void i18n.use(initReactI18next).init({
	resources,
	lng: initialLocale(),
	fallbackLng: defaultLocale,
	defaultNS: "common",
	ns: [...namespaces],
	interpolation: { escapeValue: false },
	react: { useSuspense: false },
});

export function setLocale(locale: Locale): void {
	void i18n.changeLanguage(locale);
	try {
		window.localStorage.setItem(storageKey, locale);
	} catch {
		// Without storage the choice lasts for this page only.
	}
}

export default i18n;

// A key built at runtime, such as `status.${value}`: it cannot be checked when compiling.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const dyn = (key: string): any => key;
