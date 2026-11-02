import { ITBentoGrid, ITBentoTile, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";
import { BENTO_TONE } from "./theme";

// The facts of a record, as a mosaic: a tile per fact, the first in ink when it is the one that matters most. The row
// grows with what it holds, so an address has its lines (web.md section 8).
export function FactStrip(props: { facts: { label: string; value: ReactNode; wide?: boolean }[]; perRow?: 2 | 3 | 4; hero?: boolean }) {
	const span = 12 / (props.perRow ?? 4);
	return (
		<ITBentoGrid className="sf:auto-rows-auto">
			{props.facts.map((fact, index) => (
				<ITBentoTile
					key={fact.label}
					label={fact.label}
					cols={fact.wide ? 12 : span}
					wide={fact.wide}
					tone={props.hero && index === 0 ? BENTO_TONE.ink : "plain"}
					className="sf-rise"
					style={{ animationDelay: `${index * 40}ms` }}
				>
					<ITText as="div" className="sf:mt-1 sf:whitespace-pre-line sf:text-title-sm sf:wrap-anywhere">
						{fact.value}
					</ITText>
				</ITBentoTile>
			))}
		</ITBentoGrid>
	);
}
