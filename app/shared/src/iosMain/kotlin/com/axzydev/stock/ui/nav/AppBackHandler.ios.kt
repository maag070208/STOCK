package com.axzydev.stock.ui.nav

import androidx.compose.runtime.Composable

// iOS has no back button; the screens' own back buttons are the way out.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
