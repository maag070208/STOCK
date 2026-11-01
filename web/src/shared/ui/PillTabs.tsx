import { useITAppearance } from "@axzydev/axzy_ui_system";

// Pills of navigation that change the address (the reports, the sections of a record). The bento look is the design's:
// the active one is the ink pill, the rest sit on the neutral control surface; in the other appearances the kit's own
// underline is kept. It takes `aria-current`, so the current section is announced.
export interface PillTab {
	id: string;
	label: string;
}

export function PillTabs(props: { tabs: PillTab[]; current: string; onSelect: (id: string) => void; label: string }) {
	const bento = useITAppearance() === "bento";
	return (
		<div role="tablist" aria-label={props.label} className="sf:mb-4 sf:flex sf:flex-wrap sf:items-center sf:gap-2">
			{props.tabs.map((tab) => {
				const active = tab.id === props.current;
				return (
					<button
						key={tab.id}
						type="button"
						role="tab"
						aria-selected={active}
						aria-current={active ? "page" : undefined}
						onClick={() => props.onSelect(tab.id)}
						style={
							bento
								? {
										backgroundColor: active ? "var(--it-bento-ink-bg, var(--color-on-background))" : "var(--it-bento-control-bg, var(--color-surface-soft))",
										color: active ? "var(--it-bento-ink-text, #ffffff)" : "var(--it-bento-control-text, var(--color-on-surface-muted))",
									}
								: undefined
						}
						className={
							bento
								? "sf:cursor-pointer sf:rounded-pill sf:border-0 sf:px-4 sf:py-2 sf:text-body-md sf:font-semibold sf:transition-colors sf:focus-visible:outline-2 sf:focus-visible:outline-primary"
								: "sf:cursor-pointer sf:border-0 sf:border-b-2 sf:border-transparent sf:bg-transparent sf:px-3 sf:py-2 sf:text-body-md sf:font-semibold"
						}
					>
						{tab.label}
					</button>
				);
			})}
		</div>
	);
}
