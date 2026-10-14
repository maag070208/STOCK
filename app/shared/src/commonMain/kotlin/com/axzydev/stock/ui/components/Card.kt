package com.axzydev.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow

private val spec = DesignTokens.Components.cardPanel

// The quiet card of the design: a thin border, 12dp corners and 24dp inside.
// It has no shadow: the card is transparent, and a Compose shadow would show through it.
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
	val shape = RoundedCornerShape(spec.rounded.dp)
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(Color(spec.backgroundColor), shape)
			.border(1.dp, StockFlow.colors.outline, shape)
			.padding(vertical = spec.paddingVertical.dp, horizontal = spec.paddingHorizontal.dp),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp),
		content = content,
	)
}
