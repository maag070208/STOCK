import { ITCard, ITGrid, ITStack, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";

// The facts of a record, in one slim card: a small label and its value. It replaces a row of tall stat cards, where a
// number shouted louder than the record's own title (web.md section 8).
export function FactStrip(props: { facts: { label: string; value: ReactNode }[]; perRow?: 2 | 3 | 4 }) {
	const span = 12 / (props.perRow ?? 4);
	return (
		<ITCard>
			<ITGrid container spacing={4}>
				{props.facts.map((fact) => (
					<ITGrid key={fact.label} item xs={12} sm={6} lg={span}>
						<ITStack spacing={1}>
							<ITText className="sf:text-label-md sf:uppercase sf:tracking-wide" muted>
								{fact.label}
							</ITText>
							<ITText className="sf:text-body-lg sf:font-semibold">{fact.value}</ITText>
						</ITStack>
					</ITGrid>
				))}
			</ITGrid>
		</ITCard>
	);
}
