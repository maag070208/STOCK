import { ITAlert, ITButton, ITDialog, ITInput, ITMultiSelect, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { roleLabel, type PermissionDefinition, type Role } from "@entities/permission";
import { userApi, type PermissionExceptionView, type Scope, type User } from "@entities/user";
import { dyn } from "@shared/i18n";
import { useSubmit } from "@shared/ui/useSubmit";

// The access of one person: their roles, and their exceptions with reason and expiry.
// Nobody changes their own access: the API refuses it, and the screen says why.
export function UserAccessDialog(props: { user: User | null; roles: Role[]; permissions: PermissionDefinition[]; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["users", "common"]);
	const [roles, setRoles] = useState<string[]>([]);
	const [exceptions, setExceptions] = useState<PermissionExceptionView[]>([]);
	const [draft, setDraft] = useState({ permission: "", scope: "ALL" as Scope, reason: "", expiresAt: "" });
	const rolesSubmit = useSubmit();
	const exceptionSubmit = useSubmit();
	const user = props.user;

	useEffect(() => {
		if (!user) return;
		setRoles(user.roles);
		setDraft({ permission: "", scope: "ALL", reason: "", expiresAt: "" });
		rolesSubmit.reset();
		exceptionSubmit.reset();
		void userApi.exceptions(user.id).then(setExceptions);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [user]);

	if (!user) return null;
	const permissionName = (key: string) => t(dyn(`common:permissions.${key}`), { defaultValue: key });
	const scopesOf = (key: string): Scope[] => ["NONE", ...(props.permissions.find((p) => p.key === key)?.scopes ?? [])];

	async function saveRoles() {
		if (await rolesSubmit.run(() => userApi.setRoles(user!.id, roles))) props.onSaved();
	}

	async function addException() {
		const expiresAt = draft.expiresAt ? new Date(draft.expiresAt).toISOString() : null;
		const saved = await exceptionSubmit.run(() => userApi.setException(user!.id, draft.permission, { scope: draft.scope, reason: draft.reason, expiresAt }));
		if (saved) {
			setExceptions(await userApi.exceptions(user!.id));
			setDraft({ permission: "", scope: "ALL", reason: "", expiresAt: "" });
		}
	}

	async function removeException(permission: string) {
		if ((await exceptionSubmit.run(() => userApi.removeException(user!.id, permission))) !== undefined) setExceptions(await userApi.exceptions(user!.id));
	}

	return (
		<ITDialog isOpen onClose={props.onClose} title={t("access.title", { name: user.name })}>
			<div role="dialog" aria-modal="true" aria-label={t("access.title", { name: user.name })} className="sf:flex sf:min-w-[36rem] sf:max-w-3xl sf:flex-col sf:gap-6">
				<section className="sf:flex sf:flex-col sf:gap-3">
					<h3 className="sf:text-title-md">{t("access.roles")}</h3>
					<ITMultiSelect
						name="roles"
						value={roles}
						options={props.roles.filter((role) => role.active || roles.includes(role.key)).map((role) => ({ value: role.key, label: roleLabel(role.key, role.name) }))}
						onChange={(selected) => setRoles(selected.map(String))}
					/>
					{rolesSubmit.error && <ITAlert variant="error">{rolesSubmit.error}</ITAlert>}
					<div>
						<ITButton label={t("access.saveRoles")} onClick={() => void saveRoles()} disabled={rolesSubmit.submitting} />
					</div>
				</section>

				<section className="sf:flex sf:flex-col sf:gap-3">
					<h3 className="sf:text-title-md">{t("access.exceptions")}</h3>
					<p className="sf:text-body-md sf:text-on-surface-muted">{t("access.exceptionsDescription")}</p>
					{exceptions.length === 0 ? (
						<p className="sf:text-body-md sf:text-on-surface-muted">{t("access.none")}</p>
					) : (
						<table className="sf:w-full sf:text-body-md" data-testid="exceptions">
							<thead className="sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
								<tr>
									<th className="sf:p-2 sf:text-left">{t("access.permission")}</th>
									<th className="sf:p-2 sf:text-left">{t("access.scope")}</th>
									<th className="sf:p-2 sf:text-left">{t("access.reason")}</th>
									<th className="sf:p-2 sf:text-left">{t("access.expiresAt")}</th>
									<th />
								</tr>
							</thead>
							<tbody>
								{exceptions.map((exception) => (
									<tr key={exception.permission} className="sf:border-t sf:border-outline">
										<td className="sf:p-2">{permissionName(exception.permission)}</td>
										<td className="sf:p-2">{t(dyn(`common:scope.${exception.scope}`))}</td>
										<td className="sf:p-2">{exception.reason}</td>
										<td className="sf:p-2">{exception.expiresAt ? new Date(exception.expiresAt).toLocaleString() : t("access.never")}</td>
										<td className="sf:p-2 sf:text-right">
											<ITButton variant="text" label={t("common:actions.remove")} onClick={() => void removeException(exception.permission)} />
										</td>
									</tr>
								))}
							</tbody>
						</table>
					)}
					<div className="sf:grid sf:grid-cols-1 sf:gap-3 sf:md:grid-cols-2">
						<ITSelect
							name="permission"
							label={t("access.permission")}
							value={draft.permission}
							onChange={(e) => setDraft((d) => ({ ...d, permission: e.target.value, scope: "ALL" }))}
							options={[{ value: "", label: "—" }, ...props.permissions.map((p) => ({ value: p.key, label: permissionName(p.key) }))]}
							valueField="value"
							labelField="label"
							error={exceptionSubmit.fieldErrors.permission}
						/>
						<ITSelect
							name="scope"
							label={t("access.scope")}
							value={draft.scope}
							onChange={(e) => setDraft((d) => ({ ...d, scope: e.target.value as Scope }))}
							options={(draft.permission ? scopesOf(draft.permission) : ["NONE", "ALL"]).map((scope) => ({ value: scope, label: t(dyn(`common:scope.${scope}`)) }))}
							valueField="value"
							labelField="label"
							error={exceptionSubmit.fieldErrors.scope}
						/>
						<ITInput name="reason" label={t("access.reason")} value={draft.reason} onChange={(e) => setDraft((d) => ({ ...d, reason: e.target.value }))} error={exceptionSubmit.fieldErrors.reason} touched={!!exceptionSubmit.fieldErrors.reason} />
						<div className="sf:flex sf:flex-col sf:gap-1">
							<label htmlFor="expiresAt" className="sf:text-label-md">{t("access.expiresAt")}</label>
							<input id="expiresAt" type="datetime-local" value={draft.expiresAt} onChange={(e) => setDraft((d) => ({ ...d, expiresAt: e.target.value }))} className="sf:h-10 sf:rounded-sm sf:border sf:border-outline-strong sf:bg-surface-elevated sf:px-3 sf:text-body-md" />
							{exceptionSubmit.fieldErrors.expiresAt && <span className="sf:text-body-md sf:text-danger">{exceptionSubmit.fieldErrors.expiresAt}</span>}
						</div>
					</div>
					{exceptionSubmit.error && <ITAlert variant="error">{exceptionSubmit.error}</ITAlert>}
					<div>
						<ITButton label={t("access.add")} onClick={() => void addException()} disabled={!draft.permission || exceptionSubmit.submitting} />
					</div>
				</section>
			</div>
		</ITDialog>
	);
}
