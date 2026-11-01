import { designTokens } from "./design-tokens";

const colors = designTokens.colors;

// The Axzy UI System theme, built from the design tokens. Nothing here is chosen by hand.
// `appearance: "bento"` is the design: the kit draws the light canvas, the borderless 24px cards, the pill controls and
// the ink-pill navigation. The palette only says which brand color and which neutrals it uses.
// `radius` and `shadow` are left out: the bento defaults are the design's, and passing either would replace them.
export const themeSettings = {
	appearance: "bento",
	// The reference density of the bento layout: 80% of the root size, so the dashboard and the tables fit more.
	density: 0.8,
	palette: {
		primary: colors.primary,
		secondary: colors["on-surface-muted"],
		ternary: colors["surface-soft"],
		danger: colors.danger,
		success: colors.success,
		info: colors.info,
		alert: colors["primary-warm"],
		warning: colors.warning,
		// The shell of the design: a white sidebar with ink text, and a top bar the color of the canvas.
		layout: {
			sidebarBg: colors["shell-base"],
			sidebarText: colors["on-shell"],
			navbarBg: colors.background,
			navbarText: colors["on-shell"],
		},
		// The neutral table: no orange tint on the header, ink rows on white.
		table: {
			headerBg: colors.surface,
			headerText: colors["on-surface-muted"],
			rowBg: colors["surface-elevated"],
			rowText: colors["on-surface"],
			rowHover: colors["surface-soft"],
		},
	},
} as const;
