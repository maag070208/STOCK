import { useEffect, useState } from "react";
import type { Theme } from "./animations";

// The theme in use, from the system setting. The flat app follows the operating system.
export function useTheme(): Theme {
	const [theme, setTheme] = useState<Theme>(() =>
		window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light",
	);
	useEffect(() => {
		const query = window.matchMedia("(prefers-color-scheme: dark)");
		const onChange = () => setTheme(query.matches ? "dark" : "light");
		query.addEventListener("change", onChange);
		return () => query.removeEventListener("change", onChange);
	}, []);
	return theme;
}

// True when the user asked the system for less motion.
export function useReducedMotion(): boolean {
	const [reduced, setReduced] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
	useEffect(() => {
		const query = window.matchMedia("(prefers-reduced-motion: reduce)");
		const onChange = () => setReduced(query.matches);
		query.addEventListener("change", onChange);
		return () => query.removeEventListener("change", onChange);
	}, []);
	return reduced;
}
