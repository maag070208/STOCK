package com.axzydev.stock.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

// The languages the app can show. Spanish is the default; the user chooses English on their profile.
enum class Language(val tag: String) {
	ES("es"),
	EN("en"),
}

val DefaultLanguage = Language.ES

val LocalLanguage = compositionLocalOf { DefaultLanguage }

// The text for a key, in the language in use.
@Composable
@ReadOnlyComposable
fun text(key: Text): String = textFor(key, LocalLanguage.current)
