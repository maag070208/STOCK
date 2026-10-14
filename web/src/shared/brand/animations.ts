// Path of a Lottie animation from the brand pack, for the theme in use. brand.md section 4.
export type AnimationKind = "intro" | "loader";
export type Theme = "light" | "dark";

export function animationPath(kind: AnimationKind, theme: Theme): string {
	return `/animations/stockflow-${kind}-${theme}.json`;
}
