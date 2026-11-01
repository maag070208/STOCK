import { ITAlert, ITButton, ITInput, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { useLoginForm } from "../model/useLoginForm";

export function LoginForm() {
	const { t } = useTranslation("auth");
	const form = useLoginForm();
	return (
		<form onSubmit={form.submit} noValidate>
			<ITStack spacing={4}>
				<ITStack spacing={1}>
					<ITText as="h2" className="sf:text-headline-lg sf:text-on-background">
						{t("login.title")}
					</ITText>
					<ITText className="sf:text-body-md" muted>
						{t("login.hint")}
					</ITText>
				</ITStack>
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
				<ITButton type="submit" className="sf:w-full" label={form.submitting ? t("login.submitting") : t("login.submit")} disabled={form.submitting} />
			</ITStack>
		</form>
	);
}
