import { type Column, ITAlert, ITButton, ITDialog, ITFlex, ITGrid, ITInput, ITMultiSelect, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { IconButton } from "@shared/ui/IconButton";
import { FaTrash } from "react-icons/fa";
import { roleLabel, type PermissionDefinition, type Role } from "@entities/permission";
import { userApi, type PermissionExceptionView, type Scope, type User } from "@entities/user";
import { dyn } from "@shared/i18n";
import { useSubmit } from "@shared/ui/useSubmit";
import { PlainTable } from "@shared/ui/PlainTable";

// One row of the exceptions: the exception, and the key the kit tells the rows apart by.
type ExceptionRow = Record<string, unknown> & { id: string; exception: PermissionExceptionView };

// The access of one person: their roles, and their exceptions with reason and expiry.
// Nobody changes their own access: the API refuses it, and the screen says why. The dialog is the kit's, and the two
// blocks inside are the kit's stacks, table and fields (web.md section 8).
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

	const rows: ExceptionRow[] = exceptions.map((exception) => ({ id: exception.permission, exception }));

	const columns: Column<ExceptionRow>[] = [
		{ key: "permission", label: t("access.permission"), type: "string", render: (row) => permissionName(row.exception.permission) },
		{ key: "scope", label: t("access.scope"), type: "string", render: (row) => t(dyn(`common:scope.${row.exception.scope}`)) },
		{ key: "reason", label: t("access.reason"), type: "string", render: (row) => row.exception.reason },
		{ key: "expiresAt", label: t("access.expiresAt"), type: "string", render: (row) => (row.exception.expiresAt ? new Date(row.exception.expiresAt).toLocaleString() : t("access.never")) },
		{
			key: "actions",
			label: "",
			type: "actions",
			className: "sf:text-right",
			actions: (row) => <IconButton icon={<FaTrash aria-hidden />} label={t("common:actions.remove")} onClick={() => void removeException(row.exception.permission)} />,
		},
	];

	return (
		<ITDialog isOpen onClose={props.onClose} title={t("access.title", { name: user.name })}>
			{/* The kit's dialog does not name itself as one: the role and the label are set here (web.md section 8). */}
			<ITText
				as="div"
				role="dialog"
				aria-modal="true"
				aria-label={t("access.title", { name: user.name })}
				className="sf:flex sf:min-w-[36rem] sf:max-w-3xl sf:flex-col sf:gap-6"
			>
				<ITStack spacing={3}>
					<ITText as="h3" className="sf:text-title-md">
						{t("access.roles")}
					</ITText>
					<ITMultiSelect
						name="roles"
						value={roles}
						options={props.roles.filter((role) => role.active || roles.includes(role.key)).map((role) => ({ value: role.key, label: roleLabel(role.key, role.name) }))}
						onChange={(selected) => setRoles(selected.map(String))}
					/>
					{rolesSubmit.error && <ITAlert variant="error">{rolesSubmit.error}</ITAlert>}
					<ITFlex>
						<ITButton label={t("access.saveRoles")} onClick={() => void saveRoles()} disabled={rolesSubmit.submitting} />
					</ITFlex>
				</ITStack>

				<ITStack spacing={3}>
					<ITText as="h3" className="sf:text-title-md">
						{t("access.exceptions")}
					</ITText>
					<ITText muted>{t("access.exceptionsDescription")}</ITText>
					{exceptions.length === 0 ? (
						<ITText muted>{t("access.none")}</ITText>
					) : (
						<PlainTable<ExceptionRow>
							columns={columns}
							data={rows}
							size="sm"
						/>
					)}
					<ITGrid container spacing={3}>
						<ITGrid item xs={12} sm={6}>
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
						</ITGrid>
						<ITGrid item xs={12} sm={6}>
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
						</ITGrid>
						<ITGrid item xs={12} sm={6}>
							<ITInput name="reason" label={t("access.reason")} value={draft.reason} onChange={(e) => setDraft((d) => ({ ...d, reason: e.target.value }))} error={exceptionSubmit.fieldErrors.reason} touched={!!exceptionSubmit.fieldErrors.reason} />
						</ITGrid>
						<ITGrid item xs={12} sm={6}>
							<ITStack spacing={1}>
								<ITText as="label" htmlFor="expiresAt" className="sf:text-label-md">
									{t("access.expiresAt")}
								</ITText>
								{/*
									The kit has a date and a time picker, and no single field for both. Splitting it would change the
									field and the value the API receives, so this stays a native `datetime-local`.
								*/}
								<input
									id="expiresAt"
									name="expiresAt"
									type="datetime-local"
									value={draft.expiresAt}
									onChange={(e) => setDraft((d) => ({ ...d, expiresAt: e.target.value }))}
									className="sf:h-10 sf:rounded-sm sf:border sf:border-outline-strong sf:bg-surface-elevated sf:px-3 sf:text-body-md"
								/>
								{exceptionSubmit.fieldErrors.expiresAt && <ITText className="sf:text-danger">{exceptionSubmit.fieldErrors.expiresAt}</ITText>}
							</ITStack>
						</ITGrid>
					</ITGrid>
					{exceptionSubmit.error && <ITAlert variant="error">{exceptionSubmit.error}</ITAlert>}
					<ITFlex>
						<ITButton label={t("access.add")} onClick={() => void addException()} disabled={!draft.permission || exceptionSubmit.submitting} />
					</ITFlex>
				</ITStack>
			</ITText>
		</ITDialog>
	);
}
