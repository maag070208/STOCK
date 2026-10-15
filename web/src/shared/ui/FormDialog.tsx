import { ITAlert, ITButton, ITDialog, ITFlex } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, type FormEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

// A dialog with a form: its fields, the error of the whole form if any, and Cancel / Save. Every dialog of the system
// goes through here.
//
// The screen is built with the components of the UI kit only (web.md section 8). The one exception is the form element,
// and it is the kit's own gap: `ITDialog` renders a plain panel with no `role`, no `aria-modal` and no way to send a
// form, and the kit has no form component (`ITFormBuilder` renders a grid of fields, not a form). So this component
// carries those four things — the element, its role, its label and its submit — in one place, which is why no screen
// writes them again.
export function FormDialog(props: {
	isOpen: boolean;
	title: string;
	onClose: () => void;
	onSubmit: () => void;
	submitting?: boolean;
	error?: string | null;
	submitLabel?: string;
	// A second action before the submit, for a dialog whose main button is not the only way out (F8-06).
	secondaryAction?: ReactNode;
	children: ReactNode;
}) {
	const { t } = useTranslation();
	const opener = useRef<Element | null>(null);
	const form = useRef<HTMLFormElement>(null);

	// The focus stays inside while the dialog is open, and goes back to whoever opened it (F8-08).
	useEffect(() => {
		if (!props.isOpen) return;
		opener.current = document.activeElement;
		const onKey = (event: KeyboardEvent) => {
			if (event.key !== "Tab") return;
			const focusable = [...(form.current?.querySelectorAll<HTMLElement>("input, select, textarea, button, [href], [tabindex]:not([tabindex='-1'])") ?? [])].filter(
				(element) => !element.hasAttribute("disabled") && element.offsetParent !== null,
			);
			if (focusable.length === 0) return;
			const first = focusable[0];
			const last = focusable[focusable.length - 1];
			// Tab out of the last one comes back to the first, and backwards out of the first goes to the last.
			if (!event.shiftKey && document.activeElement === last) {
				event.preventDefault();
				first.focus();
			} else if (event.shiftKey && document.activeElement === first) {
				event.preventDefault();
				last.focus();
			}
		};
		document.addEventListener("keydown", onKey, true);
		return () => {
			document.removeEventListener("keydown", onKey, true);
			if (opener.current instanceof HTMLElement) opener.current.focus();
		};
	}, [props.isOpen]);

	const submit = (event: FormEvent) => {
		event.preventDefault();
		props.onSubmit();
	};

	return (
		<ITDialog isOpen={props.isOpen} onClose={props.onClose} title={props.title}>
			<form ref={form} role="dialog" aria-modal="true" aria-label={props.title} onSubmit={submit} noValidate className="sf:flex sf:min-w-80 sf:flex-col sf:gap-4">
				{props.children}
				{props.error && <ITAlert variant="error">{props.error}</ITAlert>}
				<ITFlex justify="end" gap={2}>
					<ITButton variant="outlined" label={t("actions.cancel")} onClick={props.onClose} />
					{props.secondaryAction}
					<ITButton type="submit" label={props.submitLabel ?? t("actions.save")} disabled={props.submitting} />
				</ITFlex>
			</form>
		</ITDialog>
	);
}
