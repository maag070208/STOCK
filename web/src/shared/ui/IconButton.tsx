import { ITButton } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";

// The action of a table row: only its icon, with the name for whoever cannot see it (web.md section 8). The accessible
// name and the tooltip are the words the button would have carried as text, so a screen reader and a test find it the
// same way.
export function IconButton(props: {
	icon: ReactNode;
	/** The name of the action: it is the accessible name and the tooltip. */
	label: string;
	onClick: () => void;
	variant?: "icon-only" | "outlined" | "text";
	size?: "sm" | "md" | "lg";
	color?: "primary" | "secondary" | "danger" | "success" | "warning" | "info" | "gray";
	disabled?: boolean;
}) {
	return (
		<ITButton
			label=""
			icon={props.icon}
			variant={"icon-only"}
			size={"sm"}
			color={props.color}
			disabled={props.disabled}
			ariaLabel={props.label}
			onClick={props.onClick}
		/>
	);
}
