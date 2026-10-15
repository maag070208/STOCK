import { ITBadget, ITButton, ITConfirmDialog, ITDataTable, ITDialog, ITDropdownMenu, ITFlex, ITPage, ITText, type Column, type ITDropdownMenuItem } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, roleLabel, type PermissionDefinition, type Role } from "@entities/permission";
import { useCan, userApi, type User } from "@entities/user";
import { CreateUserDialog } from "@features/user/create-user/CreateUserDialog";
import { EditUserDialog } from "@features/user/edit-user/EditUserDialog";
import { UserAccessDialog } from "@features/user/user-access/UserAccessDialog";
import type { TableParams } from "@shared/api/table";
import { formatDateTime } from "@shared/lib/format";
import { SecretValue } from "@shared/ui/SecretValue";
import { toast } from "@app/store";
import { ApiError } from "@shared/api/client";

type Confirm = { kind: "resetPassword" | "deactivate" | "reactivate"; user: User };

export function UsersPage() {
	const { t, i18n } = useTranslation(["users", "common"]);
	const can = { create: useCan("users.create"), edit: useCan("users.edit"), remove: useCan("users.delete"), access: useCan("users.permissions") };
	const [reload, setReload] = useState(0);
	const [roles, setRoles] = useState<Role[]>([]);
	const [permissions, setPermissions] = useState<PermissionDefinition[]>([]);
	const [creating, setCreating] = useState(false);
	const [editing, setEditing] = useState<User | null>(null);
	const [access, setAccess] = useState<User | null>(null);
	const [confirm, setConfirm] = useState<Confirm | null>(null);
	const [secret, setSecret] = useState<{ title: string; description: string; password: string } | null>(null);
	const refresh = () => setReload((value) => value + 1);

	// The roles name the filter and the forms. Who cannot read them still sees the keys.
	useEffect(() => {
		if (can.access) {
			void accessApi.roles().then(setRoles);
			void accessApi.permissions().then(setPermissions);
		}
	}, [can.access]);

	const status = (user: User) =>
		!user.active ? <ITBadget color="gray" label={t("common:status.inactive")} /> : user.locked ? <ITBadget color="danger" label={t("common:status.locked")} /> : <ITBadget color="success" label={t("common:status.active")} />;

	const columns: Column<User>[] = [
		{ key: "username", label: t("columns.username"), type: "string", filter: "search", sortable: true },
		{ key: "name", label: t("columns.name"), type: "string", filter: "search", sortable: true },
		{ key: "email", label: t("columns.email"), type: "string", filter: "search", sortable: true, render: (user) => user.email ?? "" },
		{
			key: "role",
			label: t("columns.roles"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: (roles.length ? roles.map((r) => r.key) : ["ADMIN", "OFFICE", "WAREHOUSE_MANAGER", "WAREHOUSE_OPERATOR", "APP_USER"]).map((key) => ({ id: key, name: roleLabel(key, roles.find((r) => r.key === key)?.name) })) },
			render: (user) => user.roles.map((key) => roleLabel(key, roles.find((r) => r.key === key)?.name)).join(", "),
		},
		{
			key: "active",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: [{ id: "true", name: t("common:status.active") }, { id: "false", name: t("common:status.inactive") }] },
			render: status,
		},
		{ key: "lastLoginAt", label: t("columns.lastLoginAt"), type: "date", filter: "date-range", sortable: true, render: (user) => formatDateTime(user.lastLoginAt, i18n.language) },
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (user) => {
				// Each action shows only to who may use it, and only when it applies to this person.
				const items: ITDropdownMenuItem[] = [];
				if (can.edit) items.push({ id: "edit", label: t("actions.edit"), onClick: () => setEditing(user) });
				if (can.access) items.push({ id: "access", label: t("actions.access"), onClick: () => setAccess(user) });
				if (can.edit) items.push({ id: "resetPassword", label: t("actions.resetPassword"), onClick: () => setConfirm({ kind: "resetPassword", user }) });
				if (can.edit && user.locked) items.push({ id: "unlock", label: t("actions.unlock"), onClick: () => void act(() => userApi.unlock(user.id)) });
				if (can.remove && user.active) items.push({ id: "deactivate", label: t("actions.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivate", user }) });
				if (can.remove && !user.active) items.push({ id: "reactivate", label: t("actions.reactivate"), onClick: () => setConfirm({ kind: "reactivate", user }) });
				return items.length ? <ITDropdownMenu items={items} triggerLabel={t("columns.actions")} placement="bottom-end" /> : null;
			},
		},
	];

	async function act<T>(request: () => Promise<T>): Promise<T | undefined> {
		try {
			const result = await request();
			toast(t("common:saved"));
			refresh();
			return result;
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
			return undefined;
		}
	}

	async function confirmed() {
		if (!confirm) return;
		const { kind, user } = confirm;
		setConfirm(null);
		if (kind === "resetPassword") {
			const reset = await act(() => userApi.resetPassword(user.id));
			if (reset) setSecret({ title: t("reset.title"), description: t("reset.description", { name: user.name }), password: reset.temporaryPassword });
		} else if (kind === "deactivate") await act(() => userApi.deactivate(user.id));
		else await act(() => userApi.reactivate(user.id));
	}

	return (
		<ITPage
			title={t("title")}
			description={t("description")}
			noPadding
			actions={can.create ? <ITButton label={t("new")} onClick={() => setCreating(true)} /> : undefined}
		>
			<ITDataTable<User> showViewToggle={false} columns={columns} fetchData={(params) => userApi.query(params as TableParams)} reloadTrigger={reload} layout="fixed" />

			<CreateUserDialog
				isOpen={creating}
				roles={roles}
				onClose={() => setCreating(false)}
				onCreated={(user, password) => {
					refresh();
					setSecret({ title: t("created.title"), description: t("created.description", { name: user.name }), password });
				}}
			/>
			<EditUserDialog user={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); toast(t("common:saved")); refresh(); }} />
			<UserAccessDialog user={access} roles={roles} permissions={permissions} onClose={() => setAccess(null)} onSaved={() => { toast(t("common:saved")); refresh(); }} />
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(`actions.${confirm.kind}`) : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { name: confirm.user.name }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivate" ? "danger" : "primary"}
			/>
			<ITDialog isOpen={!!secret} onClose={() => setSecret(null)} title={secret?.title}>
				{secret && (
					<ITText as="div" role="dialog" aria-modal="true" aria-label={secret.title} className="sf:flex sf:max-w-lg sf:flex-col sf:gap-4" data-testid="temporary-password">
						<ITText>{secret.description}</ITText>
						<SecretValue label={t("created.password")} value={secret.password} />
						<ITFlex justify="end">
							<ITButton label={t("common:actions.close")} onClick={() => setSecret(null)} />
						</ITFlex>
					</ITText>
				)}
			</ITDialog>
		</ITPage>
	);
}
