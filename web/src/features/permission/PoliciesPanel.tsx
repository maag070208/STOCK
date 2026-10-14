import { ITBadget, ITButton, ITConfirmDialog, ITInput, ITMultiSelect, ITSelect } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, formatPolicyValue, parsePolicyValue, roleLabel, type Policy, type PolicyAction, type Role } from "@entities/permission";
import { ApiError } from "@shared/api/client";
import { dyn } from "@shared/i18n";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

const OPERATORS = ["eq", "neq", "in", "not_in", "contains", "not_contains", "gt", "gte", "lt", "lte", "exists"];

interface Draft {
	key: string;
	priority: string;
	effect: "ALLOW" | "DENY";
	action: string;
	active: boolean;
	conditions: { field: string; operator: string; value: string }[];
	roles: string[];
}

const emptyDraft = (actions: PolicyAction[]): Draft => ({ key: "", priority: "10", effect: "DENY", action: actions[0]?.key ?? "", active: true, conditions: [], roles: [] });

// Policies: they deny or allow an action under some conditions, after RBAC allowed it. Only registered actions,
// and only the fields each action exposes, can be used: the API rejects anything else.
export function PoliciesPanel(props: { policies: Policy[]; actions: PolicyAction[]; roles: Role[]; onChanged: () => void; onError: (message: string) => void }) {
	const { t } = useTranslation(["access", "common"]);
	const [editing, setEditing] = useState<{ draft: Draft; existing: boolean } | null>(null);
	const [deleting, setDeleting] = useState<Policy | null>(null);
	const submit = useSubmit();

	const open = (policy?: Policy) => {
		submit.reset();
		setEditing(
			policy
				? {
						existing: true,
						draft: {
							key: policy.key,
							priority: String(policy.priority),
							effect: policy.effect,
							action: policy.action,
							active: policy.active,
							conditions: policy.conditions.map((c) => ({ field: c.field, operator: c.operator, value: formatPolicyValue(c.value) })),
							roles: policy.roles,
						},
					}
				: { existing: false, draft: emptyDraft(props.actions) },
		);
	};

	const update = (changes: Partial<Draft>) => setEditing((current) => (current ? { ...current, draft: { ...current.draft, ...changes } } : current));
	const fields = props.actions.find((action) => action.key === editing?.draft.action)?.fields ?? [];

	async function save() {
		if (!editing) return;
		const { draft } = editing;
		const body = {
			priority: Number(draft.priority),
			effect: draft.effect,
			action: draft.action,
			active: draft.active,
			conditions: draft.conditions.map((c) => ({ field: c.field, operator: c.operator, value: c.operator === "exists" && !c.value ? true : parsePolicyValue(c.value) })),
			roles: draft.roles,
		};
		const saved = await submit.run(() => (editing.existing ? accessApi.updatePolicy(draft.key, body) : accessApi.createPolicy({ key: draft.key, ...body })));
		if (saved) {
			setEditing(null);
			props.onChanged();
		}
	}

	async function remove(policy: Policy) {
		try {
			await accessApi.deletePolicy(policy.key);
			props.onChanged();
		} catch (failure) {
			props.onError(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"));
		}
	}

	return (
		<div className="sf:flex sf:flex-col sf:gap-4">
			<div className="sf:flex sf:items-start sf:justify-between sf:gap-4">
				<p className="sf:max-w-3xl sf:text-body-md sf:text-on-surface-muted">{t("policies.description")}</p>
				<ITButton label={t("policies.new")} onClick={() => open()} />
			</div>
			{props.policies.length === 0 ? (
				<p className="sf:text-body-md sf:text-on-surface-muted">{t("policies.none")}</p>
			) : (
				<table className="sf:w-full sf:text-body-md" data-testid="policies-table">
					<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
						<tr>
							<th className="sf:p-3 sf:text-right">{t("policies.priority")}</th>
							<th className="sf:p-3 sf:text-left">{t("policies.key")}</th>
							<th className="sf:p-3 sf:text-left">{t("policies.action")}</th>
							<th className="sf:p-3 sf:text-left">{t("policies.effect")}</th>
							<th className="sf:p-3 sf:text-left">{t("policies.conditions")}</th>
							<th className="sf:p-3 sf:text-left">{t("policies.roles")}</th>
							<th className="sf:p-3" />
						</tr>
					</thead>
					<tbody>
						{props.policies.map((policy) => (
							<tr key={policy.key} className={`sf:border-t sf:border-outline ${policy.active ? "" : "sf:text-on-surface-muted"}`}>
								<td className="sf:p-3 sf:text-right">{policy.priority}</td>
								<td className="sf:p-3 sf:text-code-sm">{policy.key}</td>
								<td className="sf:p-3 sf:text-code-sm">{policy.action}</td>
								<td className="sf:p-3">
									<ITBadget color={policy.effect === "DENY" ? "danger" : "success"} label={t(dyn(`policies.effects.${policy.effect}`))} />
								</td>
								<td className="sf:p-3">
									{policy.conditions.map((c) => `${c.field} ${t(dyn(`policies.operators.${c.operator}`))} ${formatPolicyValue(c.value)}`).join(" · ")}
								</td>
								<td className="sf:p-3">{policy.roles.length ? policy.roles.map((key) => roleLabel(key, props.roles.find((r) => r.key === key)?.name)).join(", ") : t("policies.allRoles")}</td>
								<td className="sf:p-3 sf:text-right">
									<div className="sf:flex sf:justify-end sf:gap-1">
										<ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => open(policy)} />
										<ITButton size="sm" variant="text" color="danger" label={t("common:actions.delete")} onClick={() => setDeleting(policy)} />
									</div>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			)}

			<FormDialog
				isOpen={!!editing}
				title={editing?.existing ? t("policies.editTitle") : t("policies.new")}
				onClose={() => setEditing(null)}
				onSubmit={() => void save()}
				submitting={submit.submitting}
				error={submit.error}
			>
				{editing && (
					<div className="sf:flex sf:min-w-[32rem] sf:flex-col sf:gap-4">
						<div className="sf:grid sf:grid-cols-2 sf:gap-3">
							<ITInput name="key" label={t("policies.key")} value={editing.draft.key} disabled={editing.existing} onChange={(e) => update({ key: e.target.value.toUpperCase() })} error={submit.fieldErrors.key} touched={!!submit.fieldErrors.key} required />
							<ITInput name="priority" type="number" label={t("policies.priority")} value={editing.draft.priority} onChange={(e) => update({ priority: e.target.value })} error={submit.fieldErrors.priority} touched={!!submit.fieldErrors.priority} required />
							<ITSelect name="action" label={t("policies.action")} value={editing.draft.action} onChange={(e) => update({ action: e.target.value, conditions: [] })} options={props.actions.map((a) => ({ value: a.key, label: a.key }))} valueField="value" labelField="label" error={submit.fieldErrors.action} />
							<ITSelect name="effect" label={t("policies.effect")} value={editing.draft.effect} onChange={(e) => update({ effect: e.target.value as Draft["effect"] })} options={["DENY", "ALLOW"].map((e) => ({ value: e, label: t(dyn(`policies.effects.${e}`)) }))} valueField="value" labelField="label" />
						</div>
						<div className="sf:flex sf:flex-col sf:gap-2">
							<span className="sf:text-label-md">{t("policies.conditions")}</span>
							{editing.draft.conditions.map((condition, index) => {
								const set = (changes: Partial<Draft["conditions"][number]>) =>
									update({ conditions: editing.draft.conditions.map((c, i) => (i === index ? { ...c, ...changes } : c)) });
								return (
									<div key={index} className="sf:grid sf:grid-cols-[1fr_1fr_1fr_auto] sf:items-end sf:gap-2" data-condition={index}>
										<ITSelect name={`field-${index}`} label={t("policies.field")} value={condition.field} onChange={(e) => set({ field: e.target.value })} options={[{ value: "", label: "—" }, ...fields.map((f) => ({ value: f, label: f }))]} valueField="value" labelField="label" error={submit.fieldErrors[`conditions[${index}].field`]} />
										<ITSelect name={`operator-${index}`} label={t("policies.operator")} value={condition.operator} onChange={(e) => set({ operator: e.target.value })} options={OPERATORS.map((o) => ({ value: o, label: t(dyn(`policies.operators.${o}`)) }))} valueField="value" labelField="label" />
										<ITInput name={`value-${index}`} label={t("policies.value")} value={condition.value} onChange={(e) => set({ value: e.target.value })} error={submit.fieldErrors[`conditions[${index}].value`]} touched={!!submit.fieldErrors[`conditions[${index}].value`]} />
										<ITButton variant="text" label={t("common:actions.remove")} onClick={() => update({ conditions: editing.draft.conditions.filter((_, i) => i !== index) })} />
									</div>
								);
							})}
							<p className="sf:text-body-sm sf:text-on-surface-muted">{t("policies.valueHint")}</p>
							<div>
								<ITButton variant="outlined" label={t("policies.addCondition")} onClick={() => update({ conditions: [...editing.draft.conditions, { field: fields[0] ?? "", operator: "eq", value: "" }] })} />
							</div>
						</div>
						<ITMultiSelect
							name="roles"
							label={t("policies.roles")}
							placeholder={t("policies.allRoles")}
							value={editing.draft.roles}
							options={props.roles.map((role) => ({ value: role.key, label: roleLabel(role.key, role.name) }))}
							onChange={(selected) => update({ roles: selected.map(String) })}
						/>
						<label className="sf:flex sf:items-center sf:gap-2 sf:text-body-md">
							<input type="checkbox" checked={editing.draft.active} onChange={(e) => update({ active: e.target.checked })} />
							{t("policies.active")}
						</label>
					</div>
				)}
			</FormDialog>
			<ITConfirmDialog
				isOpen={!!deleting}
				onClose={() => setDeleting(null)}
				onConfirm={() => { const policy = deleting!; setDeleting(null); void remove(policy); }}
				title={t("common:actions.delete")}
				message={deleting ? t("policies.deleteConfirm", { key: deleting.key }) : ""}
				confirmLabel={t("common:actions.delete")}
				cancelLabel={t("common:actions.cancel")}
				variant="danger"
			/>
		</div>
	);
}
