import { ITAlert, ITButton, ITInput, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useChangePasswordForm } from "../model/useChangePasswordForm";

export function ChangePasswordForm({ forced, onDone }: { forced: boolean; onDone: () => void }) {
	const { t } = useTranslation("auth");
	const form = useChangePasswordForm(onDone);
	return (
		<form onSubmit={form.submit} noValidate>
			<ITStack spacing={4}>
				<ITText as="h2" className="sf:text-title-md sf:text-on-background">
					{t("changePassword.title")}
				</ITText>
				{forced && (
					<ITText as="p" className="sf:text-body-md sf:text-on-surface-muted">
						{t("changePassword.forced")}
					</ITText>
				)}
				<ITInput name="currentPassword" type="password" label={t("changePassword.current")} value={form.values.current} onChange={(e) => form.set("current")(e.target.value)} error={form.fieldErrors.current} touched={!!form.fieldErrors.current} autoFocus />
				<ITInput name="newPassword" type="password" label={t("changePassword.new")} value={form.values.next} onChange={(e) => form.set("next")(e.target.value)} error={form.fieldErrors.next} touched={!!form.fieldErrors.next} />
				<ITInput name="confirmPassword" type="password" label={t("changePassword.confirm")} value={form.values.confirm} onChange={(e) => form.set("confirm")(e.target.value)} error={form.fieldErrors.confirm} touched={!!form.fieldErrors.confirm} />
				{form.error && <ITAlert variant="error">{form.error}</ITAlert>}
				<ITButton type="submit" className="sf:w-full" label={t("changePassword.submit")} disabled={form.submitting} />
			</ITStack>
		</form>
	);
}
