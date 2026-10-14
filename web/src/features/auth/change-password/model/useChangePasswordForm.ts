import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import { authApi, tokensRefreshed, userLoaded } from "@entities/user";
import { ApiError } from "@shared/api/client";

// The password change. The API checks the current password, the length and that the new one is different;
// here the two copies of the new one must match. Afterwards every other session of the person has ended.
export function useChangePasswordForm(onDone: () => void) {
	const { t } = useTranslation(["auth", "common"]);
	const dispatch = useDispatch();
	const [values, setValues] = useState({ current: "", next: "", confirm: "" });
	const [tried, setTried] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [serverErrors, setServerErrors] = useState<Record<string, string>>({});

	const required = (value: string) => (tried && !value ? t("common:validation.required") : undefined);
	const fieldErrors = {
		current: required(values.current) ?? serverErrors.currentPassword,
		next: required(values.next) ?? serverErrors.newPassword,
		confirm: required(values.confirm) ?? (tried && values.confirm && values.confirm !== values.next ? t("auth:changePassword.mismatch") : undefined),
	};

	const set = (field: keyof typeof values) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	async function submit(event?: FormEvent) {
		event?.preventDefault();
		setTried(true);
		if (!values.current || !values.next || values.next !== values.confirm || submitting) return;
		setSubmitting(true);
		setError(null);
		setServerErrors({});
		try {
			const tokens = await authApi.changePassword(values.current, values.next);
			dispatch(tokensRefreshed(tokens));
			dispatch(userLoaded(await authApi.me()));
			onDone();
		} catch (failure) {
			if (failure instanceof ApiError && failure.details.length > 0) setServerErrors(failure.fieldErrors());
			else setError(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"));
		} finally {
			setSubmitting(false);
		}
	}

	return { values, set, fieldErrors, error, submitting, submit };
}
