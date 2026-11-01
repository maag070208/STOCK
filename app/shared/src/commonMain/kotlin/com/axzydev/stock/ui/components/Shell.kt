package com.axzydev.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.axzydev.stock.ui.theme.StockFlow

// The strip behind the system status bar, in the canvas of the mode. The status bar icons follow the mode
// (MainActivity uses the automatic style), so a light canvas keeps dark icons and a dark canvas keeps light ones.
@Composable
fun ShellStatusBar() {
	Spacer(Modifier.fillMaxWidth().background(StockFlow.colors.background).windowInsetsTopHeight(WindowInsets.statusBars))
}
