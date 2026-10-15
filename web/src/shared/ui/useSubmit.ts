import { useState } from "react";
import { ApiError } from "@shared/api/client";
import i18n from "@shared/i18n";

// Runs a form's request: while it runs `submitting` is true; a refusal of the API fills the field errors
// (by field name) or the form's error, in the person's language. `unexpected` replaces the generic message, for a form
// that says what a failure to reach the API means in its own words (F8-06).
export function useSubmit(options: { unexpected?: string } = {}) {
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

	async function run<T>(request: () => Promise<T>): Promise<T | undefined> {
		setSubmitting(true);
		setError(null);
		setFieldErrors({});
		try {
			return await request();
		} catch (failure) {
			if (failure instanceof ApiError && failure.details.length > 0) setFieldErrors(failure.fieldErrors());
			else setError(failure instanceof ApiError ? failure.message : (options.unexpected ?? i18n.t("errors.unexpected")));
			return undefined;
		} finally {
			setSubmitting(false);
		}
	}

	const reset = () => {
		setError(null);
		setFieldErrors({});
	};

	return { submitting, error, fieldErrors, run, reset };
}
