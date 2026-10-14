import { useTranslation } from "react-i18next";
import { useSessionUser } from "@entities/user";

export function HomePage() {
	const { t } = useTranslation();
	const user = useSessionUser();
	return (
		<section className="sf:flex sf:flex-col sf:gap-2">
			<h1 className="sf:text-headline-lg">{t("nav.home")}</h1>
			<p className="sf:text-body-lg sf:text-on-surface-muted">{t("home.greeting", { name: user?.name ?? "" })}</p>
			<p className="sf:text-body-md sf:text-on-surface-muted">{t("home.description")}</p>
		</section>
	);
}
