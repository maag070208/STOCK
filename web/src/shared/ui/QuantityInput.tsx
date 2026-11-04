import { ITInput } from "@axzydev/axzy_ui_system";
import type { KeyboardEvent } from "react";

// A quantity of stock: a positive number with the decimals its unit allows, and never above what the caller knows
// there is (what the warehouse has, what was picked, what a line asks). The kit's ITInput already refuses, key by
// key, a fourth decimal or a digit past `max`; this wrapper sets decimals, min and max together so every screen asks
// for the same contract, and keeps the value a string like the forms that compose it.
export function QuantityInput(props: {
	name: string;
	label?: string;
	value: string;
	onChange: (value: string) => void;
	// The decimals the unit allows: 0 (the default) is a whole piece.
	decimalPlaces?: number | null;
	// The most the field accepts; without it there is no ceiling.
	max?: number | null;
	min?: number;
	error?: string;
	touched?: boolean;
	required?: boolean;
	onKeyDown?: (event: KeyboardEvent) => void;
}) {
	return (
		<ITInput
			name={props.name}
			type="number"
			label={props.label}
			value={props.value}
			onChange={(event) => props.onChange(String(event.target.value))}
			decimals={props.decimalPlaces ?? 0}
			min={props.min}
			max={props.max ?? undefined}
			error={props.error}
			touched={props.touched}
			required={props.required}
			onKeyDown={props.onKeyDown}
		/>
	);
}
