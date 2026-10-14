import { useEffect, useState } from "react";

// Loads a Lottie file once. Returns null while it loads or if it cannot be loaded.
export function useAnimationData(path: string): object | null {
	const [data, setData] = useState<object | null>(null);
	useEffect(() => {
		let active = true;
		fetch(path)
			.then((response) => (response.ok ? response.json() : null))
			.then((json) => {
				if (active) setData(json);
			})
			.catch(() => {
				// The loader keeps showing; the intro then ends on its own timer.
			});
		return () => {
			active = false;
		};
	}, [path]);
	return data;
}
