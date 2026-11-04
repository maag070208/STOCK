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
	// The length rule of the API (5 to 72) answers while the new password is written, and the two copies must match as
	// soon as both have something to compare.
	const lengthError = (value: string) => (value && (value.length < 5 || value.length > 72) ? t("auth:changePassword.tooShort") : undefined);
	const fieldErrors = {
		current: required(values.current) ?? serverErrors.currentPassword,
		next: required(values.next) ?? lengthError(values.next) ?? serverErrors.newPassword,
		confirm: required(values.confirm) ?? (values.confirm && values.confirm !== values.next ? t("auth:changePassword.mismatch") : undefined),
	};
	const blocked = !values.current || !values.next || !!lengthError(values.next) || values.next !== values.confirm;
	// Only a mistake that is already on screen closes the button; an empty field still needs the click that says so.
	const hardBlocked = !!lengthError(values.next) || (!!values.confirm && values.next !== values.confirm);

	const set = (field: keyof typeof values) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	async function submit(event?: FormEvent) {
		event?.preventDefault();
		setTried(true);
		if (blocked || submitting) return;
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

	return { values, set, fieldErrors, error, submitting, hardBlocked, submit };
}
