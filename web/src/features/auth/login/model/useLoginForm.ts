import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch } from "react-redux";
import type { ThunkDispatch, UnknownAction } from "@reduxjs/toolkit";
import { login } from "@entities/user";
import { ApiError } from "@shared/api/client";

// The sign-in form: both fields are required here; the API checks the rest and answers in the person's language.
export function useLoginForm() {
	const { t } = useTranslation();
	const dispatch = useDispatch<ThunkDispatch<unknown, unknown, UnknownAction>>();
	const [username, setUsername] = useState("");
	const [password, setPassword] = useState("");
	const [tried, setTried] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const fieldErrors = {
		username: tried && !username.trim() ? t("validation.required") : undefined,
		password: tried && !password ? t("validation.required") : undefined,
	};

	async function submit(event?: FormEvent) {
		event?.preventDefault();
		setTried(true);
		if (!username.trim() || !password || submitting) return;
		setSubmitting(true);
		setError(null);
		try {
			await dispatch(login({ username, password })).unwrap();
		} catch (failure) {
			setError(failure instanceof ApiError ? failure.message : (failure as { message?: string }).message ?? t("errors.unexpected"));
		} finally {
			setSubmitting(false);
		}
	}

	return { username, setUsername, password, setPassword, fieldErrors, error, submitting, submit };
}
