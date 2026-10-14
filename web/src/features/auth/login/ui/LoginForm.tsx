import { ITAlert, ITButton, ITInput } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useLoginForm } from "../model/useLoginForm";

export function LoginForm() {
	const { t } = useTranslation("auth");
	const form = useLoginForm();
	return (
		<form onSubmit={form.submit} className="sf:flex sf:flex-col sf:gap-4" noValidate>
			<h2 className="sf:text-headline-lg">{t("login.title")}</h2>
			<ITInput
				name="username"
				label={t("login.username")}
				value={form.username}
				onChange={(event) => form.setUsername(event.target.value)}
				error={form.fieldErrors.username}
				touched={!!form.fieldErrors.username}
				autoFocus
			/>
			<ITInput
				name="password"
				type="password"
				label={t("login.password")}
				value={form.password}
				onChange={(event) => form.setPassword(event.target.value)}
				error={form.fieldErrors.password}
				touched={!!form.fieldErrors.password}
			/>
			{form.error && <ITAlert variant="error">{form.error}</ITAlert>}
			<ITButton type="submit" label={form.submitting ? t("login.submitting") : t("login.submit")} disabled={form.submitting} />
		</form>
	);
}
