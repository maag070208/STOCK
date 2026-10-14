import { useEffect, useState } from "react";
import { Lottie, LottieSubscription } from "lottie-react";
import { animationPath } from "../../shared/brand/animations";
import { useAnimationData } from "../../shared/brand/useAnimationData";
import { useReducedMotion, useTheme } from "../../shared/brand/useBrandPreferences";
import { BrandLoader } from "../brand-loader/BrandLoader";

const seenKey = "stockflow.intro-seen";
const introMs = 4300; // brand.md section 4: the intro is about 4.3 seconds.
const reducedMs = 1000; // reduced motion shows the first frame for a moment, then gets out of the way.

// Plays once per browser session. It never blocks sign-in for longer than its length.
export function BrandIntro() {
	const theme = useTheme();
	const reduced = useReducedMotion();
	const data = useAnimationData(animationPath("intro", theme));
	const [done, setDone] = useState(() => {
		try {
			return window.sessionStorage.getItem(seenKey) === "1";
		} catch {
			return false;
		}
	});

	useEffect(() => {
		if (done) return;
		const ms = reduced ? reducedMs : introMs;
		const timer = window.setTimeout(() => finish(), ms);
		return () => window.clearTimeout(timer);
	}, [done, reduced, data]);

	function finish() {
		try {
			window.sessionStorage.setItem(seenKey, "1");
		} catch {
			// Without storage the intro may play again on the next load.
		}
		setDone(true);
	}

	if (done) return null;

	return (
		<div
			data-testid="brand-intro"
			data-motion={reduced ? "reduced" : "full"}
			style={{ position: "fixed", inset: 0, zIndex: 50, display: "flex", alignItems: "center", justifyContent: "center" }}
		>
			{data ? (
				<Lottie
					src={data}
					loop={false}
					autoplay={!reduced}
					subscriptions={{ [LottieSubscription.complete]: finish }}
					className="sf:size-60"
				/>
			) : (
				<BrandLoader />
			)}
		</div>
	);
}
