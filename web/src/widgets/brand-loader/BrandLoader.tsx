import { ITText } from "@axzydev/axzy_ui_system";
import { Lottie } from "lottie-react";
import { useTranslation } from "react-i18next";
import { animationPath } from "../../shared/brand/animations";
import { useAnimationData } from "../../shared/brand/useAnimationData";
import { useTheme } from "../../shared/brand/useBrandPreferences";

// The loading state. Its label is read by screen readers; the animation is decorative.
export function BrandLoader() {
	const { t } = useTranslation();
	const theme = useTheme();
	const data = useAnimationData(animationPath("loader", theme));
	return (
		<ITText as="div" role="status" aria-label={t("brand.loading")}>
			{data ? <Lottie src={data} loop autoplay className="sf:size-24" /> : null}
		</ITText>
	);
}
