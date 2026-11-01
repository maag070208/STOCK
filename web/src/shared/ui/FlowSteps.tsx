import { ITCard, ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";
import { FaCheck } from "react-icons/fa";

// The way of a record through its work: the steps, where it is now, and what comes next with the action that does it.
// `current` past the last step means it is finished.
export function FlowSteps(props: { steps: string[]; current: number; next: string; action?: ReactNode }) {
	return (
		<ITCard data-testid="flow-steps">
			<ITFlex align="center" justify="between" wrap="wrap" gap={4}>
				<ITStack spacing={2} className="sf:min-w-0 sf:flex-1">
					<ITFlex align="center" wrap="wrap" gap={2}>
						{props.steps.map((step, index) => {
							const done = index < props.current;
							const now = index === props.current;
							return (
								<ITFlex key={step} align="center" gap={2}>
									{index > 0 && <ITText as="span" className={`sf:h-0.5 sf:w-6 sf:rounded-pill ${done || now ? "sf:bg-primary" : "sf:bg-outline"}`} aria-hidden />}
									<ITFlex
										align="center"
										gap={2}
										aria-current={now ? "step" : undefined}
										className={`sf:rounded-pill sf:px-3 sf:py-1.5 sf:text-label-md ${now ? "sf:bg-primary sf:text-on-primary" : done ? "sf:bg-surface-soft sf:text-on-surface" : "sf:text-on-surface-muted"}`}
									>
										{done && <FaCheck aria-hidden className="sf:text-primary" />}
										{step}
									</ITFlex>
								</ITFlex>
							);
						})}
					</ITFlex>
					<ITText className="sf:text-body-md" muted>
						{props.next}
					</ITText>
				</ITStack>
				{props.action}
			</ITFlex>
		</ITCard>
	);
}
