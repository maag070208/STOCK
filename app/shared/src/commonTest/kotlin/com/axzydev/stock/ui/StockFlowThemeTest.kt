package com.axzydev.stock.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.axzydev.stock.data.health.HealthState
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.home.healthTone
import com.axzydev.stock.ui.theme.DarkColors
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.LightColors
import com.axzydev.stock.ui.theme.toTextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

// F0-19 · unit: the app's theme is built from the design tokens, with nothing chosen by hand.
class StockFlowThemeTest {
	@Test
	fun lightColorsAreTheWorkspaceColorsOfTheDesign() {
		assertEquals(Color(DesignTokens.Colors.background), LightColors.background)
		assertEquals(Color(DesignTokens.Colors.onBackground), LightColors.onBackground)
		assertEquals(Color(DesignTokens.Colors.surfaceElevated), LightColors.surfaceElevated)
		assertEquals(Color(DesignTokens.Colors.onSurfaceMuted), LightColors.onSurfaceMuted)
		assertEquals(Color(DesignTokens.Colors.outline), LightColors.outline)
		assertEquals(Color(DesignTokens.Colors.primary), LightColors.primary)
		assertEquals(Color(DesignTokens.Colors.danger), LightColors.danger)
	}

	@Test
	fun darkModeReplacesTheWorkspaceRolesAndKeepsTheAccent() {
		assertEquals(Color(DesignTokens.DarkColors.background), DarkColors.background)
		assertEquals(Color(DesignTokens.DarkColors.onBackground), DarkColors.onBackground)
		assertEquals(Color(DesignTokens.DarkColors.surfaceElevated), DarkColors.surfaceElevated)
		assertEquals(Color(DesignTokens.DarkColors.onSurfaceMuted), DarkColors.onSurfaceMuted)
		assertEquals(Color(DesignTokens.DarkColors.outline), DarkColors.outline)
		// Orange and the semantic colors are the same in both modes.
		assertEquals(LightColors.primary, DarkColors.primary)
		assertEquals(LightColors.onPrimary, DarkColors.onPrimary)
		assertEquals(LightColors.success, DarkColors.success)
	}

	@Test
	fun aTypeStyleKeepsItsSizeWeightLineHeightAndTracking() {
		val label = DesignTokens.Typography.labelSm.toTextStyle()
		assertEquals(12.sp, label.fontSize)
		assertEquals(FontWeight(600), label.fontWeight)
		assertEquals(16.sp, label.lineHeight)
		assertEquals(0.05.em, label.letterSpacing)
		assertEquals(FontFamily.Default, label.fontFamily)
		assertEquals(FontFamily.Monospace, DesignTokens.Typography.codeSm.toTextStyle().fontFamily)
	}

	@Test
	fun eachStatusToneIsAComponentOfTheDesign() {
		assertEquals(DesignTokens.Components.statusBadgeMock, StatusTone.MOCK.spec)
		assertEquals(DesignTokens.Components.statusBadgePlanned, StatusTone.PLANNED.spec)
		assertEquals(DesignTokens.Components.statusBadgeDevelopment, StatusTone.DEVELOPMENT.spec)
		assertEquals(DesignTokens.Components.statusBadgeIntegrated, StatusTone.INTEGRATED.spec)
		assertEquals(DesignTokens.Components.statusBadgeProduction, StatusTone.PRODUCTION.spec)
	}

	@Test
	fun theApiStatusPicksItsTone() {
		assertEquals(StatusTone.PLANNED, healthTone(null))
		assertEquals(StatusTone.PRODUCTION, healthTone(HealthState.Up))
		assertEquals(StatusTone.MOCK, healthTone(HealthState.Down))
		assertEquals(StatusTone.MOCK, healthTone(HealthState.Unreachable))
	}
}
