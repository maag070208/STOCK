import { ITBadget, ITButton, ITCheckbox, ITConfirmDialog, ITFlex, ITGrid, ITInput, ITMultiSelect, ITSelect, ITStack, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, formatPolicyValue, parsePolicyValue, roleLabel, type Policy, type PolicyAction, type Role } from "@entities/permission";
import { ApiError } from "@shared/api/client";
import { dyn } from "@shared/i18n";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { PlainTable } from "@shared/ui/PlainTable";

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

// One row of the table: the policy, and the key the kit uses to tell the rows apart.
type PolicyRow = Record<string, unknown> & { id: string; policy: Policy };

const emptyDraft = (actions: PolicyAction[]): Draft => ({ key: "", priority: "10", effect: "DENY", action: actions[0]?.key ?? "", active: true, conditions: [], roles: [] });

// Policies: they deny or allow an action under some conditions, after RBAC allowed it. Only registered actions,
// and only the fields each action exposes, can be used: the API rejects anything else. The list is the kit's table and
// the form is the kit's fields (web.md section 8).
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

	const rows: PolicyRow[] = props.policies.map((policy) => ({ id: policy.key, policy }));

	const columns: Column<PolicyRow>[] = [
		{ key: "priority", label: t("policies.priority"), type: "string", className: "sf:text-right", render: (row) => String(row.policy.priority) },
		{ key: "key", label: t("policies.key"), type: "string", render: (row) => <ITText className="sf:text-code-sm" muted={!row.policy.active}>{row.policy.key}</ITText> },
		{ key: "action", label: t("policies.action"), type: "string", render: (row) => <ITText className="sf:text-code-sm" muted={!row.policy.active}>{row.policy.action}</ITText> },
		{
			key: "effect",
			label: t("policies.effect"),
			type: "string",
			render: (row) => <ITBadget color={row.policy.effect === "DENY" ? "danger" : "success"} label={t(dyn(`policies.effects.${row.policy.effect}`))} />,
		},
		{
			key: "conditions",
			label: t("policies.conditions"),
			type: "string",
			render: (row) => row.policy.conditions.map((c) => `${c.field} ${t(dyn(`policies.operators.${c.operator}`))} ${formatPolicyValue(c.value)}`).join(" · "),
		},
		{
			key: "roles",
			label: t("policies.roles"),
			type: "string",
			render: (row) => (row.policy.roles.length ? row.policy.roles.map((key) => roleLabel(key, props.roles.find((r) => r.key === key)?.name)).join(", ") : t("policies.allRoles")),
		},
		{
			key: "actions",
			label: "",
			type: "actions",
			actions: (row) => (
				<ITFlex justify="end" gap={1}>
					<ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => open(row.policy)} />
					<ITButton size="sm" variant="text" color="danger" label={t("common:actions.delete")} onClick={() => setDeleting(row.policy)} />
				</ITFlex>
			),
		},
	];

	return (
		<ITStack spacing={4}>
			<ITFlex align="start" justify="between" gap={4}>
				<ITText muted className="sf:max-w-3xl">
					{t("policies.description")}
				</ITText>
				<ITButton label={t("policies.new")} onClick={() => open()} />
			</ITFlex>
			{props.policies.length === 0 ? (
				<ITText muted>{t("policies.none")}</ITText>
			) : (
				<PlainTable<PolicyRow>
					columns={columns}
					data={rows}
					size="sm"
				/>
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
					<ITStack spacing={4} className="sf:min-w-[32rem]">
						<ITGrid container spacing={3}>
							<ITGrid item xs={12} sm={6}>
								<ITInput name="key" label={t("policies.key")} value={editing.draft.key} disabled={editing.existing} onChange={(e) => update({ key: e.target.value.toUpperCase() })} error={submit.fieldErrors.key} touched={!!submit.fieldErrors.key} required />
							</ITGrid>
							<ITGrid item xs={12} sm={6}>
								<ITInput name="priority" type="number" label={t("policies.priority")} value={editing.draft.priority} onChange={(e) => update({ priority: e.target.value })} error={submit.fieldErrors.priority} touched={!!submit.fieldErrors.priority} required />
							</ITGrid>
							<ITGrid item xs={12} sm={6}>
								<ITSelect
									name="action"
									label={t("policies.action")}
									value={editing.draft.action}
									onChange={(e) => update({ action: e.target.value, conditions: [] })}
									options={props.actions.map((a) => ({ value: a.key, label: a.key }))}
									valueField="value"
									labelField="label"
									error={submit.fieldErrors.action}
								/>
							</ITGrid>
							<ITGrid item xs={12} sm={6}>
								<ITSelect
									name="effect"
									label={t("policies.effect")}
									value={editing.draft.effect}
									onChange={(e) => update({ effect: e.target.value as Draft["effect"] })}
									options={["DENY", "ALLOW"].map((e) => ({ value: e, label: t(dyn(`policies.effects.${e}`)) }))}
									valueField="value"
									labelField="label"
								/>
							</ITGrid>
						</ITGrid>
						<ITStack spacing={2}>
							<ITText className="sf:text-label-md">{t("policies.conditions")}</ITText>
							{editing.draft.conditions.map((condition, index) => {
								const set = (changes: Partial<Draft["conditions"][number]>) =>
									update({ conditions: editing.draft.conditions.map((c, i) => (i === index ? { ...c, ...changes } : c)) });
								return (
									<ITGrid key={index} container spacing={2}>
										<ITGrid item xs={12} sm={6} md={3}>
											<ITSelect
												name={`field-${index}`}
												label={t("policies.field")}
												value={condition.field}
												onChange={(e) => set({ field: e.target.value })}
												options={[{ value: "", label: "—" }, ...fields.map((f) => ({ value: f, label: f }))]}
												valueField="value"
												labelField="label"
												error={submit.fieldErrors[`conditions[${index}].field`]}
											/>
										</ITGrid>
										<ITGrid item xs={12} sm={6} md={3}>
											<ITSelect
												name={`operator-${index}`}
												label={t("policies.operator")}
												value={condition.operator}
												onChange={(e) => set({ operator: e.target.value })}
												options={OPERATORS.map((o) => ({ value: o, label: t(dyn(`policies.operators.${o}`)) }))}
												valueField="value"
												labelField="label"
											/>
										</ITGrid>
										<ITGrid item xs={12} sm={6} md={3}>
											<ITInput name={`value-${index}`} label={t("policies.value")} value={condition.value} onChange={(e) => set({ value: e.target.value })} error={submit.fieldErrors[`conditions[${index}].value`]} touched={!!submit.fieldErrors[`conditions[${index}].value`]} />
										</ITGrid>
										<ITGrid item xs={12} sm={6} md={3}>
											<ITButton variant="text" label={t("common:actions.remove")} onClick={() => update({ conditions: editing.draft.conditions.filter((_, i) => i !== index) })} />
										</ITGrid>
									</ITGrid>
								);
							})}
							<ITText muted className="sf:text-body-sm">
								{t("policies.valueHint")}
							</ITText>
							<ITFlex>
								<ITButton variant="outlined" label={t("policies.addCondition")} onClick={() => update({ conditions: [...editing.draft.conditions, { field: fields[0] ?? "", operator: "eq", value: "" }] })} />
							</ITFlex>
						</ITStack>
						<ITMultiSelect
							name="roles"
							label={t("policies.roles")}
							placeholder={t("policies.allRoles")}
							value={editing.draft.roles}
							options={props.roles.map((role) => ({ value: role.key, label: roleLabel(role.key, role.name) }))}
							onChange={(selected) => update({ roles: selected.map(String) })}
						/>
						<ITCheckbox name="active" label={t("policies.active")} checked={editing.draft.active} onChange={(on) => update({ active: on })} />
					</ITStack>
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
		</ITStack>
	);
}
