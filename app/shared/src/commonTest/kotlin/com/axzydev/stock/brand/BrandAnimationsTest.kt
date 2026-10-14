package com.axzydev.stock.brand

import kotlin.test.Test
import kotlin.test.assertEquals

// F0-16 · unit: the animation files and the timings agree with brand.md section 4.
class BrandAnimationsTest {

	@Test
	fun `the file for each kind and theme is the one from the brand pack`() {
		assertEquals("files/animations/stockflow-intro-light.json", BrandAnimations.file(AnimationKind.INTRO, BrandTheme.LIGHT))
		assertEquals("files/animations/stockflow-intro-dark.json", BrandAnimations.file(AnimationKind.INTRO, BrandTheme.DARK))
		assertEquals("files/animations/stockflow-loader-dark.json", BrandAnimations.file(AnimationKind.LOADER, BrandTheme.DARK))
	}

	@Test
	fun `the intro lasts about four point three seconds and reduced motion holds the first frame for one second`() {
		assertEquals(4300L, BrandAnimations.INTRO_MILLIS)
		assertEquals(1000L, BrandAnimations.REDUCED_MOTION_HOLD_MILLIS)
	}
}
