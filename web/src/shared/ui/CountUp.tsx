import { useITBentoCountUp } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";

// A number of a dashboard, climbing to its value when it arrives. Null means it could not be read, and shows the dash.
export function CountUp({ value }: { value: number | null }) {
	const { i18n } = useTranslation();
	const shown = useITBentoCountUp(value ?? 0);
	return value === null ? "—" : new Intl.NumberFormat(i18n.language).format(shown);
}
