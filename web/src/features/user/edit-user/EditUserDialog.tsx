import { ITInput, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { userApi, type User } from "@entities/user";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// Name, email and language. The username cannot change, and roles are set from Access.
export function EditUserDialog(props: { user: User | null; onClose: () => void; onSaved: (user: User) => void }) {
	const { t } = useTranslation(["users", "common"]);
	const [values, setValues] = useState({ name: "", email: "", language: "es" });
	const submit = useSubmit();

	useEffect(() => {
		if (props.user) setValues({ name: props.user.name, email: props.user.email ?? "", language: props.user.language });
		submit.reset();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.user]);

	async function save() {
		if (!props.user) return;
		const saved = await submit.run(() => userApi.update(props.user!.id, values));
		if (saved) props.onSaved(saved);
	}

	const errors = submit.fieldErrors;
	return (
		<FormDialog isOpen={!!props.user} title={t("form.editTitle")} onClose={props.onClose} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
			<ITInput name="name" label={t("form.name")} value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} touched={!!errors.name} required />
			<ITInput name="email" type="email" label={t("form.email")} value={values.email} onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))} error={errors.email} touched={!!errors.email} />
			<ITSelect
				name="language"
				label={t("form.language")}
				value={values.language}
				onChange={(e) => setValues((v) => ({ ...v, language: e.target.value }))}
				options={[{ value: "es", label: t("common:language.es") }, { value: "en", label: t("common:language.en") }]}
				valueField="value"
				labelField="label"
			/>
		</FormDialog>
	);
}
