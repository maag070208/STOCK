package com.axzydev.stock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The app's look comes from design/command-center-DESIGN.md through DesignTokens, and from nowhere else.
// There is no Material theme underneath: every screen is drawn with the pieces in ui/components.

// The workspace colors. The shell and the status badges look the same in both modes,
// so they read their colors from DesignTokens.Components where they are drawn.
@Immutable
data class StockFlowColors(
	val background: Color,
	val onBackground: Color,
	val surface: Color,
	val surfaceSoft: Color,
	val surfaceElevated: Color,
	val onSurface: Color,
	val onSurfaceMuted: Color,
	val outline: Color,
	val outlineStrong: Color,
	val primary: Color,
	val primaryStrong: Color,
	val primaryWarm: Color,
	val primaryFocus: Color,
	val onPrimary: Color,
	val success: Color,
	val warning: Color,
	val danger: Color,
	val info: Color,
)

val LightColors = StockFlowColors(
	background = Color(DesignTokens.Colors.background),
	onBackground = Color(DesignTokens.Colors.onBackground),
	surface = Color(DesignTokens.Colors.surface),
	surfaceSoft = Color(DesignTokens.Colors.surfaceSoft),
	surfaceElevated = Color(DesignTokens.Colors.surfaceElevated),
	onSurface = Color(DesignTokens.Colors.onSurface),
	onSurfaceMuted = Color(DesignTokens.Colors.onSurfaceMuted),
	outline = Color(DesignTokens.Colors.outline),
	outlineStrong = Color(DesignTokens.Colors.outlineStrong),
	primary = Color(DesignTokens.Colors.primary),
	primaryStrong = Color(DesignTokens.Colors.primaryStrong),
	primaryWarm = Color(DesignTokens.Colors.primaryWarm),
	primaryFocus = Color(DesignTokens.Colors.primaryFocus),
	onPrimary = Color(DesignTokens.Colors.onPrimary),
	success = Color(DesignTokens.Colors.success),
	warning = Color(DesignTokens.Colors.warning),
	danger = Color(DesignTokens.Colors.danger),
	info = Color(DesignTokens.Colors.info),
)

// Dark mode changes the workspace only. Orange and the semantic colors stay.
val DarkColors = LightColors.copy(
	background = Color(DesignTokens.DarkColors.background),
	onBackground = Color(DesignTokens.DarkColors.onBackground),
	surface = Color(DesignTokens.DarkColors.surface),
	surfaceSoft = Color(DesignTokens.DarkColors.surfaceSoft),
	surfaceElevated = Color(DesignTokens.DarkColors.surfaceElevated),
	onSurface = Color(DesignTokens.DarkColors.onSurface),
	onSurfaceMuted = Color(DesignTokens.DarkColors.onSurfaceMuted),
	outline = Color(DesignTokens.DarkColors.outline),
	outlineStrong = Color(DesignTokens.DarkColors.outlineStrong),
)

// The system sans font, as the design asks: no branded typeface.
fun DesignTokens.TypeStyle.toTextStyle(): TextStyle =
	TextStyle(
		fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
		fontSize = fontSize.sp,
		fontWeight = FontWeight(fontWeight),
		lineHeight = lineHeight.sp,
		letterSpacing = letterSpacingEm.em,
	)

// The type scale of the design. Screens pick one of these; they do not build a style of their own.
// The app's layout, from the reference the owner chose on 2026-10-14 (design-system.md section 7): an orange hero
// above a sheet with large top corners, and fields and buttons shaped as pills. Colors and type stay the design's;
// these are the only sizes the design does not give.
object AppLayout {
	// The top corners of the sheet that holds a screen's content.
	val sheetCorner = 32.dp
	val pill = RoundedCornerShape(percent = 50)
	// A pill field or button: taller than the 48dp a thumb needs, as in the reference.
	val pillHeight = 56.dp
	// The brand tile in the hero: the glyph on a white square, so its orange box shows on the orange hero.
	val brandTile = 44.dp
	val iconSize = 22.dp
}

object StockFlowType {
	val displayHero = DesignTokens.Typography.displayHero.toTextStyle()
	val headlineXl = DesignTokens.Typography.headlineXl.toTextStyle()
	val headlineLg = DesignTokens.Typography.headlineLg.toTextStyle()
	val titleMd = DesignTokens.Typography.titleMd.toTextStyle()
	val bodyLg = DesignTokens.Typography.bodyLg.toTextStyle()
	val bodyMd = DesignTokens.Typography.bodyMd.toTextStyle()
	val bodySm = DesignTokens.Typography.bodySm.toTextStyle()
	val labelMd = DesignTokens.Typography.labelMd.toTextStyle()
	val labelSm = DesignTokens.Typography.labelSm.toTextStyle()
	val codeSm = DesignTokens.Typography.codeSm.toTextStyle()
}

private val LocalStockFlowColors = staticCompositionLocalOf { LightColors }

object StockFlow {
	val colors: StockFlowColors
		@Composable @ReadOnlyComposable get() = LocalStockFlowColors.current
}

@Composable
fun StockFlowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	CompositionLocalProvider(LocalStockFlowColors provides if (dark) DarkColors else LightColors, content = content)
}
