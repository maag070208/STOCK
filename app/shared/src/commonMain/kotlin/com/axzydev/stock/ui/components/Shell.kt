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

// The strip behind the system status bar, in the hero's orange on every screen, so the hero starts at the top edge
// and the status bar's light icons always have a strong ground.
@Composable
fun ShellStatusBar() {
	Spacer(Modifier.fillMaxWidth().background(StockFlow.colors.primary).windowInsetsTopHeight(WindowInsets.statusBars))
}
