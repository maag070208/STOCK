package com.axzydev.stock.ui.nav

import androidx.compose.runtime.Composable

// The system's back gesture, where the platform has one. It mirrors the app's own back: the deepest open screen
// closes first, and only the home lets the system leave the app. A platform without a back gesture ignores it, and
// the screens' own back buttons remain the way out.
@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
