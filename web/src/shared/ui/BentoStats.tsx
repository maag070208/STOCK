import { type ColumnFilters, ITBentoGrid, ITBentoTile, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { TableParams } from "@shared/api/table";
import { CountUp } from "./CountUp";
import { BENTO_TONE } from "./theme";

export type StatTone = keyof typeof BENTO_TONE | "plain";

export interface BentoStat {
	id: string;
	label: string;
	// A count, climbing when it arrives; null is one that could not be read. `text` takes its place for what is not a number.
	value?: number | null;
	text?: string;
	caption?: string;
	tone?: StatTone;
	icon?: ReactNode;
	active?: boolean;
	onClick?: () => void;
}

const toneOf = (tone: StatTone = "plain") => (tone === "plain" ? "plain" : BENTO_TONE[tone]);

// The numbers of a screen, as a mosaic: one row of tiles that share the width, the first one wider when they do not divide
// it evenly. A tile with `onClick` is the filter of the list below, and the one in use says so.
export function BentoStats({ items, label }: { items: BentoStat[]; label: string }) {
	const span = Math.floor(12 / items.length);
	return (
		<ITText as="div" role="group" aria-label={label} className="sf-stats sf:mb-4">
			<ITBentoGrid>
				{items.map((item, index) => (
					<ITBentoTile
						key={item.id}
						label={item.label}
						icon={item.icon}
						cols={index === 0 ? span + 12 - items.length * span : span}
						rows={1}
						tone={toneOf(item.tone)}
						value={item.text ?? <CountUp value={item.value ?? null} />}
						caption={item.caption}
						onClick={item.onClick}
						className={`sf-rise ${item.active ? "sf:ring-2 sf:ring-primary" : ""}`}
						style={{ animationDelay: `${index * 50}ms` }}
					/>
				))}
			</ITBentoGrid>
		</ITText>
	);
}

// One total per name, read once at a time and again when `reload` changes. A count that fails stays empty and the rest
// still arrive: a number never takes the screen down. A query left out (no permission) is never asked for.
export function useCounts<K extends string>(queries: Partial<Record<K, () => Promise<{ total: number }>>>, reload?: unknown) {
	const [counts, setCounts] = useState<Partial<Record<K, number | null>>>({});
	const latest = useRef(queries);
	latest.current = queries;
	useEffect(() => {
		let live = true;
		for (const [key, query] of Object.entries(latest.current) as [K, () => Promise<{ total: number }>][]) {
			void query()
				.then((page) => page.total)
				.catch(() => null)
				.then((total) => {
					if (live) setCounts((current) => ({ ...current, [key]: total }));
				});
		}
		return () => {
			live = false;
		};
	}, [reload]);
	return counts;
}

type ActiveFilter = "" | "true" | "false";

// The three tiles of a catalog that is deactivated, not deleted: all of it, the active and the inactive rows. They are the
// filter of its table.
export function useActiveStats(query: (params: TableParams) => Promise<{ total: number }>, reload?: unknown) {
	const { t } = useTranslation();
	const [active, setActive] = useState<ActiveFilter>("");
	const count = (filters: TableParams["filters"]) => () => query({ page: 1, limit: 1, filters });
	const counts = useCounts({ all: count({}), active: count({ active: "true" }), inactive: count({ active: "false" }) }, reload);
	const filters = useMemo(() => (active ? { active } : {}) as ColumnFilters, [active]);
	const stats: BentoStat[] = [
		{ id: "all", label: t("stats.all"), value: counts.all, tone: "ink", active: active === "", onClick: () => setActive("") },
		{ id: "active", label: t("stats.active"), value: counts.active, tone: "orange", active: active === "true", onClick: () => setActive("true") },
		{ id: "inactive", label: t("stats.inactive"), value: counts.inactive, tone: "sand", active: active === "false", onClick: () => setActive("false") },
	];
	return { stats, filters };
}
