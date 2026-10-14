import { ITButton, ITInput, ITSelect } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { sharesError, sharesTotal, type ShareInput, type StaffMember } from "@entities/invoice";
import { formatQuantity } from "@shared/lib/format";

// The shares of one line: which operators of the warehouse's template receive it, and how much each one. It shows the
// total against the line and the first problem, so the form can stay closed until the split adds up.
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
		<div className="sf:flex sf:flex-col sf:gap-2" data-testid={props.name ?? "shares"}>
			{props.shares.map((share, index) => (
				<div key={index} className="sf:grid sf:grid-cols-[1fr_8rem_auto] sf:items-end sf:gap-2">
					<ITSelect
						name={`operator-${index}`}
						label={t("form.operator")}
						value={share.operatorId}
						onChange={(e) => set(index, { operatorId: e.target.value })}
						options={props.staff.map((member) => ({ value: member.id, label: member.name }))}
						valueField="value"
						labelField="label"
					/>
					<ITInput name={`share-${index}`} type="number" label={t("form.share")} value={share.quantity} onChange={(e) => set(index, { quantity: e.target.value })} />
					<ITButton variant="text" label={t("form.remove")} onClick={() => props.onChange(props.shares.filter((_, i) => i !== index))} />
				</div>
			))}
			<div className="sf:flex sf:flex-wrap sf:items-center sf:justify-between sf:gap-2">
				<ITButton size="sm" variant="outlined" label={t("form.addShare")} onClick={() => props.onChange([...props.shares, { operatorId: "", quantity: "" }])} disabled={props.staff.length === 0} />
				<span className="sf:text-body-md sf:tabular-nums">
					{t("form.total", { assigned: formatQuantity(sharesTotal(props.shares), props.decimals, i18n.language), line: formatQuantity(props.line || 0, props.decimals, i18n.language) })}
				</span>
			</div>
			{props.staff.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{props.emptyMessage ?? t("form.noStaff")}</p>}
			{props.showErrors && error && (
				<p className="sf:text-body-md sf:text-danger" role="alert">
					{t(`shareErrors.${error}`)}
				</p>
			)}
		</div>
	);
}
