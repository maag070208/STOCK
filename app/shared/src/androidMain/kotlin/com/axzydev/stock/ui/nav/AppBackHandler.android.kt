package com.axzydev.stock.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

// Android's back gesture and back button, which the app hands to the navigation of the hub.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
	BackHandler(enabled = enabled, onBack = onBack)
}
