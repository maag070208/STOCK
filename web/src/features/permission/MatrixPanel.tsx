import { type Column, ITAlert, ITBadget, ITButton, ITField, ITFlex, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, matrixRows, roleLabel, type MatrixCell, type PermissionDefinition, type Role } from "@entities/permission";
import type { Scope } from "@entities/user";
import { dyn } from "@shared/i18n";
import { useSubmit } from "@shared/ui/useSubmit";
import { PlainTable } from "@shared/ui/PlainTable";

// One row of the matrix: a permission and the scope it has in each of the active roles.
type MatrixRow = Record<string, unknown> & { id: string; permission: PermissionDefinition; scopes: Scope[] };

// The role matrix: one row per permission, one column per role, the scope in each cell.
// Changes are kept until saved together. The API rejects a change that would leave nobody able to manage access.
// The table is the kit's, and each cell is one of its selects, labelled for who cannot see the header (web.md section 8).
export function MatrixPanel(props: { permissions: PermissionDefinition[]; roles: Role[]; cells: MatrixCell[]; onSaved: () => void }) {
	const { t } = useTranslation(["access", "common"]);
	const [changes, setChanges] = useState<Record<string, Scope>>({});
	const submit = useSubmit();
	const roles = props.roles.filter((role) => role.active);
	const rows = useMemo(() => matrixRows(props.permissions, roles, props.cells), [props.permissions, roles, props.cells]);
	const id = (roleKey: string, permissionKey: string) => `${roleKey}|${permissionKey}`;

	function change(roleKey: string, permissionKey: string, scope: Scope, original: Scope) {
		setChanges((current) => {
			const next = { ...current };
			if (scope === original) delete next[id(roleKey, permissionKey)];
			else next[id(roleKey, permissionKey)] = scope;
			return next;
		});
	}

	async function save() {
		const cells = Object.entries(changes).map(([key, scope]) => {
			const [roleKey, permissionKey] = key.split("|");
			return { roleKey, permissionKey, scope };
		});
		if (await submit.run(() => accessApi.changeMatrix(cells))) {
			setChanges({});
			props.onSaved();
		}
	}

	const data: MatrixRow[] = rows.map(({ permission, scopes }) => ({ id: permission.key, permission, scopes }));

	const columns: Column<MatrixRow>[] = [
		{
			key: "permission",
			label: t("matrix.permission"),
			type: "string",
			render: (row) => (
				<ITStack spacing={1}>
					<ITFlex align="center" gap={2}>
						<ITText>{t(dyn(`common:${row.permission.nameKey}`), { defaultValue: row.permission.key })}</ITText>
						{row.permission.sensitive && <ITBadget color="warning" label={t("matrix.sensitive")} />}
					</ITFlex>
					<ITText muted className="sf:text-code-sm">
						{row.permission.key}
					</ITText>
				</ITStack>
			),
		},
		...roles.map((role, index) => ({
			key: role.key,
			label: roleLabel(role.key, role.name),
			type: "string" as const,
			render: (row: MatrixRow) => {
				const cell = id(role.key, row.permission.key);
				const original = row.scopes[index];
				const value = changes[cell] ?? original;
				return (
					// The label of the select is the pair it changes: a screen reader reads it, and the table stays dense.
					<ITField label={`${roleLabel(role.key, role.name)} · ${row.permission.key}`} htmlFor={cell} labelClassName="sf:sr-only">
						<ITSelect
							name={cell}
							value={value}
							onChange={(event) => change(role.key, row.permission.key, event.target.value as Scope, original)}
							options={(["NONE", ...row.permission.scopes] as Scope[]).map((scope) => ({ value: scope, label: t(dyn(`common:scope.${scope}`)) }))}
							valueField="value"
							labelField="label"
						/>
					</ITField>
				);
			},
		})),
	];

	const pending = Object.keys(changes).length;
	return (
		<ITStack spacing={4}>
			<ITFlex justify="end" align="center" gap={3}>
				{pending > 0 && <ITText muted>{t("matrix.pending", { count: pending })}</ITText>}
				<ITButton label={t("matrix.save")} onClick={() => void save()} disabled={pending === 0 || submit.submitting} />
			</ITFlex>
			{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
			<PlainTable<MatrixRow>
				columns={columns}
				data={data}
				size="sm"
			/>
		</ITStack>
	);
}
