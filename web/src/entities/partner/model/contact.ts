import type { ContactInput } from "./types";

export type ContactErrorCode = "FIELD_REQUIRED" | "CONTACT_CHANNEL_REQUIRED";

// A contact needs a name, and an email or a phone, so the business can reach it (suppliers.md section 5).
// The API checks the same, and the format of each.
export function contactErrors(values: ContactInput): Partial<Record<keyof ContactInput, ContactErrorCode>> {
	const errors: Partial<Record<keyof ContactInput, ContactErrorCode>> = {};
	if (!values.name.trim()) errors.name = "FIELD_REQUIRED";
	if (!values.email.trim() && !values.phone.trim()) {
		errors.email = "CONTACT_CHANNEL_REQUIRED";
		errors.phone = "CONTACT_CHANNEL_REQUIRED";
	}
	return errors;
}

export const canSaveContact = (values: ContactInput): boolean => Object.keys(contactErrors(values)).length === 0;
