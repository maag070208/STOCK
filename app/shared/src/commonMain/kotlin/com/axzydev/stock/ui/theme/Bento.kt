package com.axzydev.stock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The pastel tiles of the bento design: [Plain] is a white tile with no tint, [Brand] the accent color, and the rest
 * the sea / lavender / mint / sun / rose surfaces the reference uses. One tone per meaning, always the same meaning
 * (redesign plan, and the web's design-system.md section 2).
 */
enum class BentoTone { Plain, Brand, Sea, Lavender, Mint, Sun, Rose }

/** The two colors of a tone: its surface ([container]) and the ink and icons on it ([content]). */
@Immutable
data class BentoToneColors(val container: Color, val content: Color)

// The bento look of the app, from design/stockflow-DESIGN.md: a soft gray canvas, white 24dp cards, 20dp tiles, pill
// controls and near-black ink. The workspace colors come from the tokens; the pastel pairs are the design's, the same
// tones as the web (they are not derived from the brand, which stays the orange accent).
object AppBento {
	val card = 24.dp
	val tile = 20.dp
	val fieldCorner = 16.dp
	val fieldHeight = 52.dp
	val buttonHeight = 48.dp
	val topBarButton = 40.dp
	val pill = RoundedCornerShape(percent = 50)

	@Composable
	fun isLight(): Boolean = StockFlow.colors.background.luminance() > 0.5f

	/** The near-black "ink" of the active controls. */
	@Composable
	fun ink(): Color = if (isLight()) Color(0xFF14171C) else Color(0xFFF1F3F6)

	/** What is written on the ink. */
	@Composable
	fun onInk(): Color = if (isLight()) Color(0xFFFFFFFF) else Color(0xFF14171C)

	/** The neutral fill of an input. */
	@Composable
	fun field(): Color = StockFlow.colors.surfaceSoft

	@Composable
	fun tone(tone: BentoTone): BentoToneColors {
		val light = isLight()
		return when (tone) {
			BentoTone.Plain -> BentoToneColors(StockFlow.colors.surfaceElevated, StockFlow.colors.onSurface)
			BentoTone.Brand -> BentoToneColors(StockFlow.colors.primary, StockFlow.colors.onPrimary)
			BentoTone.Sea ->
				if (light) BentoToneColors(Color(0xFFE0F0F7), Color(0xFF0B4A66))
				else BentoToneColors(Color(0xFF12303D), Color(0xFFA9DCF0))
			BentoTone.Lavender ->
				if (light) BentoToneColors(Color(0xFFECE7FA), Color(0xFF4A3A8C))
				else BentoToneColors(Color(0xFF2A2447), Color(0xFFCFC4F5))
			BentoTone.Mint ->
				if (light) BentoToneColors(Color(0xFFDDF3E8), Color(0xFF12643F))
				else BentoToneColors(Color(0xFF143A2A), Color(0xFF9BE0BE))
			BentoTone.Sun ->
				if (light) BentoToneColors(Color(0xFFFDF1D2), Color(0xFF7A5200))
				else BentoToneColors(Color(0xFF3F3114), Color(0xFFF3D187))
			BentoTone.Rose ->
				if (light) BentoToneColors(Color(0xFFFCE4E6), Color(0xFF9B2335))
				else BentoToneColors(Color(0xFF41191F), Color(0xFFF5B5BD))
		}
	}
}
