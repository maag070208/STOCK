package com.axzydev.stock.brand

// Animations from the brand pack (docs/architecture/brand.md section 4). The files are copied, not edited.
enum class AnimationKind { INTRO, LOADER }

enum class BrandTheme { LIGHT, DARK }

object BrandAnimations {
	const val INTRO_MILLIS = 4300L
	const val REDUCED_MOTION_HOLD_MILLIS = 1000L

	fun file(kind: AnimationKind, theme: BrandTheme): String {
		val name = when (kind) {
			AnimationKind.INTRO -> "intro"
			AnimationKind.LOADER -> "loader"
		}
		val variant = when (theme) {
			BrandTheme.LIGHT -> "light"
			BrandTheme.DARK -> "dark"
		}
		return "files/animations/stockflow-$name-$variant.json"
	}
}
