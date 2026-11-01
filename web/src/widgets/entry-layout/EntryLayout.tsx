import { ITCard, ITFlex, ITGrid, ITStack, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

// The entry screen: sign-in and the forced password change. It is composed only with the Axzy UI System, so it follows
// the kit's theme in both modes. The bento look of the design is a brand panel in the accent color next to the form's
// card; on a phone the brand stays as a compact header and the card takes the screen.
export function EntryLayout({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	const features = [t("entry.scan"), t("entry.count"), t("entry.place")];
	return (
		<ITFlex as="main" direction="column" align="center" justify="center" className="sf:min-h-screen sf:bg-background sf:px-4 sf:py-10">
			<ITStack spacing={4} className="sf:w-full sf:max-w-6xl">
				{/* The compact brand of a phone: the logo and the name, above the card. */}
				<ITFlex align="center" gap={3} className="sf:px-2 sf:lg:hidden">
					<img src="/brand/stockflow-glyph-light.svg" alt="" className="sf:size-9" />
					<ITText as="h1" className="sf:text-headline-lg sf:text-on-background">
						{t("app.name")}
					</ITText>
				</ITFlex>

				<ITGrid container columns={12} spacing={6}>
					{/* The brand panel of the design: the accent color, the promise and what the system does. */}
					<ITGrid item xs={12} lg={5} className="sf:hidden sf:lg:block">
						<ITCard className="sf:h-full" style={{ background: "var(--sf-color-primary)", border: "none" }}>
							<ITStack spacing={8} className="sf:h-full sf:justify-between">
								<ITStack spacing={5}>
									<ITFlex align="center" gap={3}>
										<img src="/brand/stockflow-glyph-dark.svg" alt="" className="sf:size-11" />
										<ITText as="h1" className="sf-on-primary sf:text-title-md">
											{t("app.name")}
										</ITText>
									</ITFlex>
									<ITText as="h2" className="sf-on-primary sf:text-headline-lg">
										{t("entry.tagline")}
									</ITText>
									<ITText className="sf-on-primary sf:text-body-md">
										{t("entry.subtitle")}
									</ITText>
								</ITStack>
								<ITStack spacing={3}>
									{features.map((feature) => (
										<ITFlex key={feature} align="center" gap={3}>
											<ITFlex
												align="center"
												justify="center"
												className="sf:size-6 sf:shrink-0 sf:rounded-pill"
												style={{ background: "color-mix(in srgb, var(--sf-color-on-primary) 22%, transparent)" }}
											>
												<ITText as="span" className="sf-on-primary sf:text-label-sm">
													✓
												</ITText>
											</ITFlex>
											<ITText className="sf-on-primary sf:text-body-md">
												{feature}
											</ITText>
										</ITFlex>
									))}
								</ITStack>
							</ITStack>
						</ITCard>
					</ITGrid>

					{/* The form: the kit's card, the same in both modes. */}
					<ITGrid item xs={12} lg={7}>
						<ITCard className="sf:h-full">{children}</ITCard>
					</ITGrid>
				</ITGrid>
			</ITStack>
		</ITFlex>
	);
}
