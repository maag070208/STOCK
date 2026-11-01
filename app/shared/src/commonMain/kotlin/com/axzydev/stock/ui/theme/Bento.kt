package com.axzydev.stock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The tiles of the bento design, in StockFlow's own family: [Plain] is white, [Ink] the dark hero, [Brand] the accent, and
 * [Orange], [Amber] and [Sand] the warm tints. [Rose] is for what was cancelled. One tone per meaning, as on the web.
 */
enum class BentoTone { Plain, Brand, Ink, Orange, Amber, Sand, Rose }

/** The two colors of a tone: its surface ([container]) and the ink and icons on it ([content]). */
@Immutable
data class BentoToneColors(val container: Color, val content: Color)

// The bento look of the app, from design/stockflow-DESIGN.md: a soft gray canvas, white 24dp cards, 20dp tiles, pill
// controls and near-black ink. Every color comes from the tokens of the design document.
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
	fun ink(): Color = Color(if (isLight()) DesignTokens.Colors.onBackground else DesignTokens.Colors.darkOnSurface)

	/** What is written on the ink. */
	@Composable
	fun onInk(): Color = Color(if (isLight()) DesignTokens.Colors.surface else DesignTokens.Colors.onBackground)

	/** The neutral fill of an input. */
	@Composable
	fun field(): Color = StockFlow.colors.surfaceSoft

	@Composable
	fun tone(tone: BentoTone): BentoToneColors {
		val light = isLight()
		fun pair(bg: Long, fg: Long, darkBg: Long, darkFg: Long) = if (light) BentoToneColors(Color(bg), Color(fg)) else BentoToneColors(Color(darkBg), Color(darkFg))
		return with(DesignTokens.Colors) {
			when (tone) {
				BentoTone.Plain -> BentoToneColors(StockFlow.colors.surfaceElevated, StockFlow.colors.onSurface)
				BentoTone.Brand -> BentoToneColors(StockFlow.colors.primary, StockFlow.colors.onPrimary)
				BentoTone.Ink -> pair(toneInkBg, toneInkFg, darkToneInkBg, darkToneInkFg)
				BentoTone.Orange -> pair(toneOrangeBg, toneOrangeFg, darkToneOrangeBg, darkToneOrangeFg)
				BentoTone.Amber -> pair(toneAmberBg, toneAmberFg, darkToneAmberBg, darkToneAmberFg)
				BentoTone.Sand -> pair(toneSandBg, toneSandFg, darkToneSandBg, darkToneSandFg)
				BentoTone.Rose -> pair(toneRoseBg, toneRoseFg, darkToneRoseBg, darkToneRoseFg)
			}
		}
	}
}
