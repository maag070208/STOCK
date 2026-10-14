import { ITAlert, ITButton, ITDialog } from "@axzydev/axzy_ui_system";
import type { FormEvent, ReactNode } from "react";
import { useTranslation } from "react-i18next";

// A dialog with a form: its fields, the error of the whole form if any, and Cancel / Save.
export function FormDialog(props: {
	isOpen: boolean;
	title: string;
	onClose: () => void;
	onSubmit: () => void;
	submitting?: boolean;
	error?: string | null;
	submitLabel?: string;
	children: ReactNode;
}) {
	const { t } = useTranslation();
	const submit = (event: FormEvent) => {
		event.preventDefault();
		props.onSubmit();
	};
	return (
		<ITDialog isOpen={props.isOpen} onClose={props.onClose} title={props.title}>
			{/* The kit's dialog does not name itself as one: the role is set here, so screen readers announce it. */}
			<form role="dialog" aria-modal="true" aria-label={props.title} onSubmit={submit} noValidate className="sf:flex sf:min-w-80 sf:flex-col sf:gap-4">
				{props.children}
				{props.error && <ITAlert variant="error">{props.error}</ITAlert>}
				<div className="sf:flex sf:justify-end sf:gap-2">
					<ITButton variant="outlined" label={t("actions.cancel")} onClick={props.onClose} />
					<ITButton type="submit" label={props.submitLabel ?? t("actions.save")} disabled={props.submitting} />
				</div>
			</form>
		</ITDialog>
	);
}
