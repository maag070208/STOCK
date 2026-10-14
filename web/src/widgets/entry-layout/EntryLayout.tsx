import { ITCard, ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

// The entry screen: sign-in and the forced password change. It is composed only with the
// Axzy UI System (ITFlex / ITStack / ITText / ITCard), so it follows the kit's theme in both modes.
export function EntryLayout({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	return (
		<ITFlex
			as="main"
			direction="column"
			align="center"
			justify="center"
			className="sf:min-h-screen sf:bg-background sf:px-4 sf:py-12"
		>
			<ITStack spacing={6} alignItems="center" className="sf:w-full sf:max-w-sm">
				<ITStack spacing={3} alignItems="center">
					<img src="/brand/stockflow-glyph-light.svg" alt="" className="sf:size-16" />
					<ITText as="h1" className="sf:text-headline-lg sf:text-on-background">
						{t("app.name")}
					</ITText>
				</ITStack>
				<ITCard className="sf:w-full">{children}</ITCard>
			</ITStack>
		</ITFlex>
	);
}
