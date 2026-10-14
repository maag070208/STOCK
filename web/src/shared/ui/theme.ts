import { designTokens } from "./design-tokens";

const colors = designTokens.colors;

// The Axzy UI System theme, built from the design tokens. Nothing here is chosen by hand.
// The kit's `radius` prop is left out: it sets one value for every corner, and the design has four.
export const themeSettings = {
	appearance: "flat",
	shadow: 1,
	palette: {
		primary: colors.primary,
		secondary: colors["on-surface-muted"],
		ternary: colors["status-integrated-fg"],
		danger: colors.danger,
		success: colors.success,
		info: colors.info,
		alert: colors["primary-focus"],
		warning: colors.warning,
		// The shell's glass is set in index.css through the kit's tokens: in light mode the kit turns a dark shell white.
		layout: {
			sidebarBg: colors["shell-base"],
			sidebarText: colors["on-shell"],
			navbarBg: colors["shell-base"],
			navbarText: colors["on-shell"],
		},
		// The neutral table, as the design prefers for new work.
		table: {
			headerBg: colors.surface,
			headerText: colors["on-surface"],
			rowBg: colors["surface-elevated"],
			rowText: colors["on-surface-muted"],
			rowHover: colors["surface-soft"],
		},
	},
} as const;
