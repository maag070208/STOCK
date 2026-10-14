import { ITAlert, ITButton, ITInput } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useChangePasswordForm } from "../model/useChangePasswordForm";

export function ChangePasswordForm({ forced, onDone }: { forced: boolean; onDone: () => void }) {
	const { t } = useTranslation("auth");
	const form = useChangePasswordForm(onDone);
	return (
		<form onSubmit={form.submit} className="sf:flex sf:flex-col sf:gap-4" noValidate>
			<h2 className="sf:text-headline-lg">{t("changePassword.title")}</h2>
			{forced && <p className="sf:text-body-md sf:text-on-surface-muted">{t("changePassword.forced")}</p>}
			<ITInput name="currentPassword" type="password" label={t("changePassword.current")} value={form.values.current} onChange={(e) => form.set("current")(e.target.value)} error={form.fieldErrors.current} touched={!!form.fieldErrors.current} autoFocus />
			<ITInput name="newPassword" type="password" label={t("changePassword.new")} value={form.values.next} onChange={(e) => form.set("next")(e.target.value)} error={form.fieldErrors.next} touched={!!form.fieldErrors.next} />
			<ITInput name="confirmPassword" type="password" label={t("changePassword.confirm")} value={form.values.confirm} onChange={(e) => form.set("confirm")(e.target.value)} error={form.fieldErrors.confirm} touched={!!form.fieldErrors.confirm} />
			{form.error && <ITAlert variant="error">{form.error}</ITAlert>}
			<ITButton type="submit" label={t("changePassword.submit")} disabled={form.submitting} />
		</form>
	);
}
