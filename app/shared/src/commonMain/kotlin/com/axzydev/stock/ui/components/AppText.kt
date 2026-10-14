package com.axzydev.stock.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

// Text in one of the design's type styles (StockFlowType). The app draws all of its text through this.
@Composable
fun AppText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
	BasicText(
		text = text,
		modifier = modifier,
		style = style,
		color = { color },
		maxLines = maxLines,
		overflow = TextOverflow.Ellipsis,
	)
}
