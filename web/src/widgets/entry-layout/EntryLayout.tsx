import { ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

// The entry screen: a split page. The form sits on the workspace canvas on the left; the panel on the right is a flat
// isometric scene of the product — cartons on a shelf platform and a scanned label — with the figures floating slowly.
// On a phone the panel is hidden and the form takes the screen. There is no language control here: the language is
// chosen inside the system, in the top bar.
export function EntryLayout({ children }: { children: ReactNode }) {
	const { t } = useTranslation();
	return (
		<ITFlex as="main" className="sf:min-h-screen sf:bg-background">
			{/* The form column. */}
			<ITFlex className="sf:min-h-screen sf:w-full sf:items-center sf:justify-center sf:bg-surface sf:px-6 sf:py-10 sf:lg:w-5/12">
				<ITStack spacing={8} className="sf:w-full sf:max-w-sm">
					<ITFlex align="center" gap={3}>
						<img src="/brand/stockflow-glyph-light.svg" alt="" className="sf:size-10" />
						<ITText as="h1" className="sf:text-headline-lg sf:text-on-background">
							{t("app.name")}
						</ITText>
					</ITFlex>
					{children}
					<ITText className="sf:text-label-sm" muted>
						StockFlow · {t("entry.footer")}
					</ITText>
				</ITStack>
			</ITFlex>

			{/* The illustration panel, hidden on a phone. */}
			<ITFlex
				className="sf:hidden sf:min-h-screen sf:w-7/12 sf:items-center sf:justify-center sf:overflow-hidden sf:p-10 sf:lg:flex"
				style={{ background: "var(--sf-color-surface-soft)" }}
			>
				<EntryArt />
			</ITFlex>
		</ITFlex>
	);
}

// A flat isometric scene of the warehouse: a shelf platform with cartons, a crate and a scanned label. The figures
// float, bob and pulse (index.css, and off when the person asked the system for less motion); it is decorative, so it
// says nothing to a screen reader.
function EntryArt() {
	return (
		<svg viewBox="0 0 640 560" className="sf:w-full sf:max-w-3xl" role="presentation" aria-hidden="true">
			{/* The quiet marks of the background. */}
			<g className="sf-art-ring">
				<circle cx="104" cy="112" r="48" fill="none" stroke="var(--sf-color-outline)" strokeWidth="2" />
			</g>
			<circle cx="566" cy="142" r="26" fill="none" stroke="var(--sf-color-outline)" strokeWidth="2" />
			<circle cx="84" cy="342" r="9" fill="var(--sf-color-primary-warm)" className="sf-art-dot" />
			<circle cx="590" cy="404" r="7" fill="var(--sf-color-outline-strong)" />
			<rect x="520" y="84" width="16" height="16" rx="4" fill="none" stroke="var(--sf-color-outline-strong)" strokeWidth="2" />
			<rect x="124" y="468" width="14" height="14" rx="4" fill="var(--sf-color-outline)" />

			{/* Everything on the shelf floats as one. */}
			<g className="sf-art-scene">
				{/* The platform, with a thin edge so it reads as a board. */}
				<polygon points="320,338 548,448 320,558 92,448" fill="var(--sf-color-surface-soft)" stroke="var(--sf-color-outline)" strokeWidth="2" />
				<polygon points="320,320 548,430 320,540 92,430" fill="var(--sf-color-surface)" stroke="var(--sf-color-outline)" strokeWidth="2" />

				{/* The white stock carton, at the back, with a small parcel on top. */}
				<g className="sf-art-carton-a">
					<polygon points="420,274 482,243 420,212 358,243" fill="var(--sf-color-surface)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					<polygon points="358,243 420,274 420,392 358,361" fill="var(--sf-color-surface-soft)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					<polygon points="420,274 482,243 482,361 420,392" fill="var(--sf-color-surface)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					{/* The parcel on the top face. */}
					<polygon points="420,212 458,193 420,174 382,193" fill="var(--sf-color-primary-warm)" />
					<polygon points="382,193 420,212 420,246 382,227" fill="var(--sf-color-primary-strong)" />
					<polygon points="420,212 458,193 458,227 420,246" fill="var(--sf-color-primary)" />
				</g>

				{/* The small crate. */}
				<g className="sf-art-carton-b">
					<polygon points="154,446 194,426 154,406 114,426" fill="var(--sf-color-surface-soft)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					<polygon points="114,426 154,446 154,482 114,462" fill="var(--sf-color-outline)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					<polygon points="154,446 194,426 194,462 154,482" fill="var(--sf-color-outline-strong)" stroke="var(--sf-color-outline)" strokeWidth="2" />
				</g>

				{/* The orange carton, at the front. */}
				<g className="sf-art-carton-c">
					<polygon points="250,344 332,303 250,262 168,303" fill="var(--sf-color-primary-warm)" />
					<polygon points="168,303 250,344 250,430 168,389" fill="var(--sf-color-primary-strong)" />
					<polygon points="250,344 332,303 332,389 250,430" fill="var(--sf-color-primary)" />
				</g>
			</g>

			{/* The scanned label: it drifts, and its scan line runs down the bars. */}
			<g className="sf-art-label">
				<g transform="rotate(-7 545 356)">
					<rect x="470" y="300" width="150" height="112" rx="18" fill="var(--sf-color-surface)" stroke="var(--sf-color-outline)" strokeWidth="2" />
					<rect x="486" y="320" width="8" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="498" y="320" width="4" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="506" y="320" width="12" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="522" y="320" width="5" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="531" y="320" width="9" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="544" y="320" width="4" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="552" y="320" width="11" height="46" rx="2" fill="var(--sf-color-on-background)" />
					<rect x="486" y="380" width="118" height="8" rx="4" fill="var(--sf-color-outline)" />
					<rect x="486" y="392" width="76" height="8" rx="4" fill="var(--sf-color-outline)" />
					<rect className="sf-art-scan" x="482" y="320" width="126" height="4" rx="2" fill="var(--sf-color-primary)" />
				</g>
			</g>

			{/* The check of a completed count. */}
			<g className="sf-art-check">
				<circle cx="596" cy="298" r="27" fill="var(--sf-color-primary)" />
				<path d="M584 298 l9 9 l17 -19" fill="none" stroke="var(--sf-color-on-primary)" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
			</g>
		</svg>
	);
}
