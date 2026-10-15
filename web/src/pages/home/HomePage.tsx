import { ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useSessionUser } from "@entities/user";

export function HomePage() {
	const { t } = useTranslation();
	const user = useSessionUser();
	return (
		<ITStack spacing={2}>
			<ITText as="h1" className="sf:text-headline-lg">
				{t("nav.home")}
			</ITText>
			<ITText className="sf:text-body-lg" muted>
				{t("home.greeting", { name: user?.name ?? "" })}
			</ITText>
			<ITText muted>{t("home.description")}</ITText>
		</ITStack>
	);
}
