import { ITBadget, ITButton, ITConfirmDialog, ITInput } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, roleLabel, type Role } from "@entities/permission";
import { ApiError } from "@shared/api/client";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// The roles. A system role keeps its name and stays active; a custom one can be renamed, deactivated and deleted.
export function RolesPanel(props: { roles: Role[]; onChanged: (message?: string) => void; onError: (message: string) => void }) {
	const { t } = useTranslation(["access", "common"]);
	const [creating, setCreating] = useState(false);
	const [renaming, setRenaming] = useState<Role | null>(null);
	const [deleting, setDeleting] = useState<Role | null>(null);
	const [values, setValues] = useState({ key: "", name: "" });
	const submit = useSubmit();

	async function create() {
		if (await submit.run(() => accessApi.createRole(values.key, values.name))) {
			setCreating(false);
			setValues({ key: "", name: "" });
			props.onChanged();
		}
	}

	async function rename() {
		if (renaming && (await submit.run(() => accessApi.updateRole(renaming.key, { name: values.name })))) {
			setRenaming(null);
			props.onChanged();
		}
	}

	async function run(request: () => Promise<unknown>) {
		try {
			await request();
			props.onChanged();
		} catch (failure) {
			props.onError(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"));
		}
	}

	return (
		<div className="sf:flex sf:flex-col sf:gap-4">
			<div className="sf:flex sf:justify-end">
				<ITButton label={t("roles.new")} onClick={() => { submit.reset(); setValues({ key: "", name: "" }); setCreating(true); }} />
			</div>
			<table className="sf:w-full sf:text-body-md" data-testid="roles-table">
				<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
					<tr>
						<th className="sf:p-3 sf:text-left">{t("roles.key")}</th>
						<th className="sf:p-3 sf:text-left">{t("roles.name")}</th>
						<th className="sf:p-3 sf:text-left">{t("roles.type")}</th>
						<th className="sf:p-3 sf:text-right">{t("roles.users")}</th>
						<th className="sf:p-3 sf:text-left">{t("roles.status")}</th>
						<th className="sf:p-3 sf:text-right">{t("roles.actions")}</th>
					</tr>
				</thead>
				<tbody>
					{props.roles.map((role) => (
						<tr key={role.key} className="sf:border-t sf:border-outline">
							<td className="sf:p-3 sf:text-code-sm">{role.key}</td>
							<td className="sf:p-3">{roleLabel(role.key, role.name)}</td>
							<td className="sf:p-3 sf:text-on-surface-muted">{role.system ? t("roles.system") : t("roles.custom")}</td>
							<td className="sf:p-3 sf:text-right">{role.users}</td>
							<td className="sf:p-3">
								<ITBadget color={role.active ? "success" : "gray"} label={role.active ? t("common:status.active") : t("common:status.inactive")} />
							</td>
							<td className="sf:p-3 sf:text-right">
								{!role.system && (
									<div className="sf:flex sf:justify-end sf:gap-1">
										<ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => { submit.reset(); setValues({ key: role.key, name: role.name ?? "" }); setRenaming(role); }} />
										<ITButton size="sm" variant="text" label={role.active ? t("roles.deactivate") : t("roles.activate")} onClick={() => void run(() => accessApi.updateRole(role.key, { active: !role.active }))} />
										<ITButton size="sm" variant="text" color="danger" label={t("common:actions.delete")} onClick={() => setDeleting(role)} />
									</div>
								)}
							</td>
						</tr>
					))}
				</tbody>
			</table>

			<FormDialog isOpen={creating} title={t("roles.new")} onClose={() => setCreating(false)} onSubmit={() => void create()} submitting={submit.submitting} error={submit.error} submitLabel={t("common:actions.create")}>
				<ITInput name="key" label={t("roles.key")} value={values.key} onChange={(e) => setValues((v) => ({ ...v, key: e.target.value.toUpperCase() }))} error={submit.fieldErrors.key ?? t("roles.keyHint")} touched={!!submit.fieldErrors.key} required />
				<ITInput name="name" label={t("roles.name")} value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={submit.fieldErrors.name} touched={!!submit.fieldErrors.name} required />
			</FormDialog>
			<FormDialog isOpen={!!renaming} title={t("common:actions.edit")} onClose={() => setRenaming(null)} onSubmit={() => void rename()} submitting={submit.submitting} error={submit.error}>
				<ITInput name="name" label={t("roles.name")} value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={submit.fieldErrors.name} touched={!!submit.fieldErrors.name} required />
			</FormDialog>
			<ITConfirmDialog
				isOpen={!!deleting}
				onClose={() => setDeleting(null)}
				onConfirm={() => { const role = deleting!; setDeleting(null); void run(() => accessApi.deleteRole(role.key)); }}
				title={t("common:actions.delete")}
				message={deleting ? t("roles.deleteConfirm", { name: roleLabel(deleting.key, deleting.name) }) : ""}
				confirmLabel={t("common:actions.delete")}
				cancelLabel={t("common:actions.cancel")}
				variant="danger"
			/>
		</div>
	);
}
