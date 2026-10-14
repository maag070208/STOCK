import { ITAlert, ITBadget, ITButton } from "@axzydev/axzy_ui_system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, matrixRows, roleLabel, type MatrixCell, type PermissionDefinition, type Role } from "@entities/permission";
import type { Scope } from "@entities/user";
import { dyn } from "@shared/i18n";
import { useSubmit } from "@shared/ui/useSubmit";

// The role matrix: one row per permission, one column per role, the scope in each cell.
// Changes are kept until saved together. The API rejects a change that would leave nobody able to manage access.
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

	const pending = Object.keys(changes).length;
	return (
		<div className="sf:flex sf:flex-col sf:gap-4">
			<div className="sf:flex sf:items-center sf:justify-end sf:gap-3">
				{pending > 0 && <span className="sf:text-body-md sf:text-on-surface-muted">{t("matrix.pending", { count: pending })}</span>}
				<ITButton label={t("matrix.save")} onClick={() => void save()} disabled={pending === 0 || submit.submitting} />
			</div>
			{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
			<div className="sf:overflow-x-auto">
				<table className="sf:w-full sf:text-body-md" data-testid="matrix">
					<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
						<tr>
							<th className="sf:p-3 sf:text-left">{t("matrix.permission")}</th>
							{roles.map((role) => (
								<th key={role.key} className="sf:p-3 sf:text-left">{roleLabel(role.key, role.name)}</th>
							))}
						</tr>
					</thead>
					<tbody>
						{rows.map(({ permission, scopes }) => (
							<tr key={permission.key} className="sf:border-t sf:border-outline">
								<td className="sf:p-3">
									<div className="sf:flex sf:items-center sf:gap-2">
										<span>{t(dyn(`common:${permission.nameKey}`), { defaultValue: permission.key })}</span>
										{permission.sensitive && <ITBadget color="warning" label={t("matrix.sensitive")} />}
									</div>
									<span className="sf:text-code-sm sf:text-on-surface-muted">{permission.key}</span>
								</td>
								{roles.map((role, index) => {
									const original = scopes[index];
									const value = changes[id(role.key, permission.key)] ?? original;
									return (
										<td key={role.key} className="sf:p-2">
											<select
												aria-label={`${role.key} ${permission.key}`}
												data-cell={id(role.key, permission.key)}
												value={value}
												onChange={(event) => change(role.key, permission.key, event.target.value as Scope, original)}
												className={`sf:h-9 sf:w-full sf:rounded-sm sf:border sf:px-2 sf:text-body-md sf:focus:outline-2 sf:focus:outline-primary-focus ${
													value !== original ? "sf:border-primary sf:bg-primary-warm/20" : "sf:border-outline-strong sf:bg-surface-elevated"
												}`}
											>
												{(["NONE", ...permission.scopes] as Scope[]).map((scope) => (
													<option key={scope} value={scope}>
														{t(dyn(`common:scope.${scope}`))}
													</option>
												))}
											</select>
										</td>
									);
								})}
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</div>
	);
}
