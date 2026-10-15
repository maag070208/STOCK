// The focus inside a dialog (F8-08): Tab wraps at both ends, so it never leaves the modal while it is open, and a form
// that is refused sends the focus to the first field with a problem.

// Where the focus goes next, from the index it is on: `step` is 1 for Tab and -1 for Shift+Tab. A dialog with nothing
// focusable answers null, and an index that is outside the controls starts at the first (or the last, going backwards).
export function focusTrapIndex(count: number, current: number, step: 1 | -1): number | null {
	if (count <= 0) return null;
	if (current < 0 || current >= count) return step === 1 ? 0 : count - 1;
	return (current + step + count) % count;
}

// The first field with an error, in the order the fields are shown: where the focus goes when a form is refused.
export function firstInvalidField(errors: Record<string, unknown>, fields: readonly string[]): string | null {
	return fields.find((field) => field in errors) ?? null;
}
