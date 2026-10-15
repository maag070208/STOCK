// The trail of a page (web.md section 8): it starts at Home, then the sections the page hangs from, and the page itself
// last. The last crumb is plain text; the ones before it are the way back, and they navigate with the router, because
// the app is a HashRouter and an `href` would leave it.
export interface Crumb {
	label: string;
	href?: string;
	onClick?: () => void;
}

// `home` is the word for the first crumb, already translated by the screen (each one has its own translation function).
export function trail(home: string, navigate: (path: string) => void, ...rest: Crumb[]): Crumb[] {
	return [{ label: home, onClick: () => navigate("/") }, ...rest];
}
