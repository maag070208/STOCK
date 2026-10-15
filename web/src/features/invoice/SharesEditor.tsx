import { ITAlert, ITButton, ITFlex, ITGrid, ITInput, ITSearchSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { sharesError, sharesTotal, type ShareInput, type StaffMember } from "@entities/invoice";
import { formatQuantity } from "@shared/lib/format";

// The shares of one line: which operators of the warehouse's template receive it, and how much each one. It shows the
// total against the line and the first problem, so the form can stay closed until the split adds up. A section with the
// kit inside it, and the kit's own text and alert for what it says (web.md section 8).
export function SharesEditor(props: {
	line: string;
	decimals: number | null;
	staff: StaffMember[];
	shares: ShareInput[];
	onChange: (shares: ShareInput[]) => void;
	showErrors: boolean;
	name?: string;
	// What to say when there is nobody to choose: by default, that the warehouse's template is empty.
	emptyMessage?: string;
}) {
	const { t, i18n } = useTranslation(["invoices", "common"]);
	const error = sharesError(props.line, props.shares);
	const set = (index: number, change: Partial<ShareInput>) => props.onChange(props.shares.map((share, i) => (i === index ? { ...share, ...change } : share)));

	return (
		<ITStack spacing={2}>
				{props.shares.map((share, index) => (
					<ITGrid key={index} container spacing={2}>
						<ITGrid item xs={12} sm={6}>
							<ITSearchSelect
								name={`operator-${index}`}
								label={t("form.operator")}
								value={share.operatorId}
								onChange={(value) => set(index, { operatorId: String(value) })}
								options={props.staff.map((member) => ({ value: member.id, label: member.name }))}
								valueField="value"
								labelField="label"
							/>
						</ITGrid>
						<ITGrid item xs={8} sm={3}>
							<ITInput name={`share-${index}`} type="number" label={t("form.share")} value={share.quantity} onChange={(e) => set(index, { quantity: e.target.value })} />
						</ITGrid>
						<ITGrid item xs={4} sm={3}>
							<ITButton variant="text" label={t("form.remove")} onClick={() => props.onChange(props.shares.filter((_, i) => i !== index))} />
						</ITGrid>
					</ITGrid>
				))}
				<ITFlex justify="between" align="center" wrap="wrap" gap={2}>
					<ITButton size="sm" variant="outlined" label={t("form.addShare")} onClick={() => props.onChange([...props.shares, { operatorId: "", quantity: "" }])} disabled={props.staff.length === 0} />
					<ITText className="sf:tabular-nums">
						{t("form.total", { assigned: formatQuantity(sharesTotal(props.shares), props.decimals, i18n.language), line: formatQuantity(props.line || 0, props.decimals, i18n.language) })}
					</ITText>
				</ITFlex>
				{props.staff.length === 0 && <ITText muted>{props.emptyMessage ?? t("form.noStaff")}</ITText>}
			{props.showErrors && error && <ITAlert variant="error">{t(`shareErrors.${error}`)}</ITAlert>}
		</ITStack>
	);
}
