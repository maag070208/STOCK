import { ITBadget, ITButton, ITConfirmDialog, ITFlex, ITInput, ITStack, ITText, type Column } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, roleLabel, type Role } from "@entities/permission";
import { ApiError } from "@shared/api/client";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";
import { PlainTable } from "@shared/ui/PlainTable";

// One row of the table: the role, and the key the kit tells the rows apart by.
type RoleRow = Record<string, unknown> & { id: string; role: Role };

// The roles. A system role keeps its name and stays active; a custom one can be renamed, deactivated and deleted.
// The list is the kit's table (web.md section 8).
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

	const rows: RoleRow[] = props.roles.map((role) => ({ id: role.key, role }));

	const columns: Column<RoleRow>[] = [
		{ key: "key", label: t("roles.key"), type: "string", render: (row) => <ITText className="sf:text-code-sm">{row.role.key}</ITText> },
		{ key: "name", label: t("roles.name"), type: "string", render: (row) => roleLabel(row.role.key, row.role.name) },
		{ key: "type", label: t("roles.type"), type: "string", render: (row) => <ITText muted>{row.role.system ? t("roles.system") : t("roles.custom")}</ITText> },
		{ key: "users", label: t("roles.users"), type: "string", className: "sf:text-right", render: (row) => String(row.role.users) },
		{
			key: "status",
			label: t("roles.status"),
			type: "string",
			render: (row) => <ITBadget color={row.role.active ? "success" : "gray"} label={row.role.active ? t("common:status.active") : t("common:status.inactive")} />,
		},
		{
			key: "actions",
			label: t("roles.actions"),
			type: "actions",
			className: "sf:text-right",
			actions: (row) =>
				row.role.system ? null : (
					<ITFlex justify="end" gap={1}>
						<ITButton size="sm" variant="text" label={t("common:actions.edit")} onClick={() => { submit.reset(); setValues({ key: row.role.key, name: row.role.name ?? "" }); setRenaming(row.role); }} />
						<ITButton size="sm" variant="text" label={row.role.active ? t("roles.deactivate") : t("roles.activate")} onClick={() => void run(() => accessApi.updateRole(row.role.key, { active: !row.role.active }))} />
						<ITButton size="sm" variant="text" color="danger" label={t("common:actions.delete")} onClick={() => setDeleting(row.role)} />
					</ITFlex>
				),
		},
	];

	return (
		<ITStack spacing={4}>
			<ITFlex justify="end">
				<ITButton label={t("roles.new")} onClick={() => { submit.reset(); setValues({ key: "", name: "" }); setCreating(true); }} />
			</ITFlex>
			<PlainTable<RoleRow>
				columns={columns}
				data={rows}
				size="sm"
			/>

			<FormDialog isOpen={creating} title={t("roles.new")} onClose={() => setCreating(false)} onSubmit={() => void create()} submitting={submit.submitting} error={submit.error} submitLabel={t("common:actions.create")}>
				<ITStack spacing={4}>
					<ITInput name="key" label={t("roles.key")} value={values.key} onChange={(e) => setValues((v) => ({ ...v, key: e.target.value.toUpperCase() }))} error={submit.fieldErrors.key ?? t("roles.keyHint")} touched={!!submit.fieldErrors.key} required />
					<ITInput name="name" label={t("roles.name")} value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={submit.fieldErrors.name} touched={!!submit.fieldErrors.name} required />
				</ITStack>
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
		</ITStack>
	);
}
