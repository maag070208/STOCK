package com.axzydev.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.toTextStyle

// The five status tones of the design: pale yellow, soft gray, pale blue, pale lavender and pale green.
enum class StatusTone(val spec: DesignTokens.Component) {
	MOCK(DesignTokens.Components.statusBadgeMock),
	PLANNED(DesignTokens.Components.statusBadgePlanned),
	DEVELOPMENT(DesignTokens.Components.statusBadgeDevelopment),
	INTEGRATED(DesignTokens.Components.statusBadgeIntegrated),
	PRODUCTION(DesignTokens.Components.statusBadgeProduction),
}

@Composable
fun StatusBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
	val spec = tone.spec
	AppText(
		text = text,
		style = requireNotNull(spec.typography).toTextStyle(),
		color = Color(spec.textColor),
		modifier = modifier
			.background(Color(spec.backgroundColor), RoundedCornerShape(spec.rounded.dp))
			.padding(vertical = spec.paddingVertical.dp, horizontal = spec.paddingHorizontal.dp),
		maxLines = 1,
	)
}
