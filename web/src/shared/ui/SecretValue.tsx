import { ITButton, ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";

// A value shown once to be passed on, such as a temporary password, with a copy button.
export function SecretValue({ label, value }: { label: string; value: string }) {
	const { t } = useTranslation();
	const [copied, setCopied] = useState(false);
	async function copy() {
		try {
			await navigator.clipboard.writeText(value);
			setCopied(true);
		} catch {
			// Without clipboard access the value can still be selected and copied by hand.
		}
	}
	return (
		<ITStack spacing={2}>
			<ITText className="sf:text-label-md">{label}</ITText>
			<ITFlex align="center" gap={3}>
				<code data-testid="secret-value" className="sf:flex-1 sf:select-all sf:rounded-sm sf:border sf:border-outline-strong sf:bg-surface sf:px-3 sf:py-2 sf:text-code-sm">
					{value}
				</code>
				<ITButton variant="outlined" label={copied ? t("actions.copied") : t("actions.copy")} onClick={() => void copy()} />
			</ITFlex>
		</ITStack>
	);
}
