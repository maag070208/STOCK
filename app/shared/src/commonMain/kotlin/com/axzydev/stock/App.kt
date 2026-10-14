package com.axzydev.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axzydev.stock.brand.BrandIntro
import com.axzydev.stock.brand.BrandTheme
import com.axzydev.stock.brand.reducedMotionEnabled
import com.axzydev.stock.data.health.HealthClient
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.session.AuthApi
import com.axzydev.stock.data.session.secureTokenStorage
import com.axzydev.stock.feature.lookup.LookupViewModel
import com.axzydev.stock.feature.session.SessionState
import com.axzydev.stock.feature.session.SessionViewModel
import com.axzydev.stock.i18n.DefaultLanguage
import com.axzydev.stock.i18n.Language
import com.axzydev.stock.i18n.LocalLanguage
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.textFor
import com.axzydev.stock.ui.components.ShellStatusBar
import com.axzydev.stock.ui.home.HomeScreen
import com.axzydev.stock.ui.lookup.LookupScreen
import com.axzydev.stock.ui.signin.ChangePasswordScreen
import com.axzydev.stock.ui.signin.SignInScreen
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowTheme

// The intro plays once per launch of the app.
private object IntroOnce {
	var played = false
}

// The screens after signing in. The home is the start; each flow returns to it.
private enum class Screen { HOME, LOOKUP }

@Composable
fun App(
	client: HealthClient = remember { HealthClient(platformEngine(), apiBaseUrl) },
	session: SessionViewModel = viewModel {
		SessionViewModel(AuthApi(platformEngine(), apiBaseUrl) { DefaultLanguage }, secureTokenStorage()) {
			textFor(Text.HEALTH_UNREACHABLE, DefaultLanguage)
		}
	},
) {
	// A saved session is looked for while the intro plays.
	LaunchedEffect(session) { session.restore() }
	val state by session.state.collectAsState()
	// Once signed in, the app speaks the person's language. Before that, Spanish.
	val language = (state as? SessionState.SignedIn)?.user?.language?.let { tag -> Language.entries.firstOrNull { it.tag == tag } } ?: DefaultLanguage
	val currentLanguage by rememberUpdatedState(language)
	val lookup = viewModel {
		LookupViewModel(ProductApi(platformEngine(), apiBaseUrl) { currentLanguage }, session, { textFor(Text.FIELD_REQUIRED, currentLanguage) }) {
			textFor(Text.HEALTH_UNREACHABLE, currentLanguage)
		}
	}

	CompositionLocalProvider(LocalLanguage provides language) {
		StockFlowTheme {
			// The workspace canvas, with the shell's strip behind the status bar on every screen.
			Column(Modifier.fillMaxSize().background(StockFlow.colors.background)) {
				ShellStatusBar()
				var introDone by remember { mutableStateOf(IntroOnce.played) }
				if (!introDone) {
					BrandIntro(
						theme = if (isSystemInDarkTheme()) BrandTheme.DARK else BrandTheme.LIGHT,
						reduced = reducedMotionEnabled(),
					) {
						IntroOnce.played = true
						introDone = true
					}
				} else {
					when (val current = state) {
						SessionState.Restoring -> Unit
						is SessionState.SignedOut -> SignInScreen(current, session::signIn)
						is SessionState.PasswordChange -> ChangePasswordScreen(current, session::changePassword)
						is SessionState.SignedIn -> {
							// Kept when the phone turns and Android rebuilds the screen.
							var screen by rememberSaveable(current.user.id) { mutableStateOf(Screen.HOME) }
							when (screen) {
								Screen.HOME -> HomeScreen(
									client = client,
									user = current.user,
									onSignOut = session::signOut,
									// Looking up a product needs products.view (barcode-scanning.md section 5).
									onLookup = if (current.user.permissions["products.view"].let { it != null && it != "NONE" }) {
										{ screen = Screen.LOOKUP }
									} else null,
								)
								Screen.LOOKUP -> LookupScreen(lookup) {
									lookup.clear()
									screen = Screen.HOME
								}
							}
						}
					}
				}
			}
		}
	}
}
