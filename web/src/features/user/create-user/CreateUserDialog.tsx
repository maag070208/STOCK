import { ITInput, ITMultiSelect, ITSelect } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { roleLabel, type Role } from "@entities/permission";
import { useCan, userApi, type User } from "@entities/user";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// A new person with a temporary password the API generates. Giving roles needs the per-person access permission.
export function CreateUserDialog(props: { isOpen: boolean; roles: Role[]; onClose: () => void; onCreated: (user: User, temporaryPassword: string) => void }) {
	const { t } = useTranslation(["users", "common"]);
	const canGrant = useCan("users.permissions");
	const [values, setValues] = useState({ username: "", name: "", email: "", language: "es", roles: [] as string[] });
	const submit = useSubmit();
	const set = (field: keyof typeof values) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	function close() {
		setValues({ username: "", name: "", email: "", language: "es", roles: [] });
		submit.reset();
		props.onClose();
	}

	async function save() {
		const created = await submit.run(() =>
			userApi.create({ ...values, email: values.email || undefined, roles: canGrant ? values.roles : undefined }),
		);
		if (created) {
			props.onCreated(created.user, created.temporaryPassword);
			close();
		}
	}

	const errors = submit.fieldErrors;
	return (
		<FormDialog isOpen={props.isOpen} title={t("form.title")} onClose={close} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error} submitLabel={t("common:actions.create")}>
			<ITInput name="username" label={t("form.username")} value={values.username} onChange={(e) => set("username")(e.target.value)} error={errors.username} touched={!!errors.username} required />
			<ITInput name="name" label={t("form.name")} value={values.name} onChange={(e) => set("name")(e.target.value)} error={errors.name} touched={!!errors.name} required />
			<ITInput name="email" type="email" label={t("form.email")} value={values.email} onChange={(e) => set("email")(e.target.value)} error={errors.email} touched={!!errors.email} />
			<ITSelect
				name="language"
				label={t("form.language")}
				value={values.language}
				onChange={(e) => set("language")(e.target.value)}
				options={[{ value: "es", label: t("common:language.es") }, { value: "en", label: t("common:language.en") }]}
				valueField="value"
				labelField="label"
			/>
			{canGrant && (
				<ITMultiSelect
					name="roles"
					label={t("form.roles")}
					value={values.roles}
					options={props.roles.filter((role) => role.active).map((role) => ({ value: role.key, label: roleLabel(role.key, role.name) }))}
					onChange={(selected) => setValues((current) => ({ ...current, roles: selected.map(String) }))}
					error={errors.roles}
					touched={!!errors.roles}
				/>
			)}
		</FormDialog>
	);
}
