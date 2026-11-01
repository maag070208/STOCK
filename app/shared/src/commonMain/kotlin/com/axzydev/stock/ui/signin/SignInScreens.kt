package com.axzydev.stock.ui.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.AppInfo
import com.axzydev.stock.feature.session.SessionState
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoPageHeader
import com.axzydev.stock.ui.components.BentoScreen
import com.axzydev.stock.ui.components.BentoSectionTitle
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowTheme
import com.axzydev.stock.ui.theme.StockFlowType
import org.jetbrains.compose.resources.painterResource
import stock_flow.shared.generated.resources.Res
import stock_flow.shared.generated.resources.stockflow_glyph_light

// The button sits this far from the end of the page, so the keyboard does not cover it while a field has the cursor.
private val BUTTON_SCROLL_STOP = 32.dp

// The entry page is a tall hero over a short form. On a phone, the keyboard covers the button at the end of the form,
// and `imePadding` grows the scrolled content instead of shrinking its window, so the framework does not move the page
// by itself. While a field has the cursor, the page follows the end of the content as it grows: that keeps the button
// in sight. The page is a single scroll of the canvas, so the offset the old sheet needed is gone: the stop is just
// enough to keep the button above the keyboard. Returns the page's state and the handler the fields report their focus to.
@Composable
private fun rememberButtonScroll(): Pair<ScrollState, (Boolean) -> Unit> {
	val scrollState = rememberScrollState()
	val stop = with(LocalDensity.current) { BUTTON_SCROLL_STOP.roundToPx() }
	var editing by remember { mutableStateOf(false) }
	LaunchedEffect(editing) {
		if (editing) snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo((it - stop).coerceAtLeast(0)) }
	}
	return scrollState to { focused: Boolean -> editing = focused }
}

// The entry screens: the bento canvas with the brand on top and the form on a white card. They are the brand moment,
// so they keep the light look in dark mode too (the same entry look as the web).
@Composable
private fun EntryScreen(headline: Text, scrollState: ScrollState, form: @Composable () -> Unit) {
	StockFlowTheme(dark = false) {
		BentoScreen(scrollState = scrollState) {
			Spacer(Modifier.height(DesignTokens.Spacing.xl.dp))
			Column(
				Modifier.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp),
			) {
				Image(painterResource(Res.drawable.stockflow_glyph_light), contentDescription = null, modifier = Modifier.height(56.dp))
				AppText(AppInfo.NAME, StockFlowType.headlineLg, StockFlow.colors.onBackground)
			}
			BentoPageHeader(text(headline))
			Card { form() }
		}
	}
}

@Composable
fun SignInScreen(state: SessionState.SignedOut, onSignIn: (String, String) -> Unit) {
	var username by rememberSaveable { mutableStateOf("") }
	var password by rememberSaveable { mutableStateOf("") }
	var tried by rememberSaveable { mutableStateOf(false) }
	val (scrollState, onFieldFocus) = rememberButtonScroll()
	val required = text(Text.FIELD_REQUIRED)
	val submit = {
		tried = true
		if (username.isNotBlank() && password.isNotEmpty() && !state.submitting) onSignIn(username, password)
	}
	EntryScreen(Text.SIGN_IN_HEADLINE, scrollState) {
		BentoSectionTitle(text(Text.SIGN_IN_TITLE), text(Text.SIGN_IN_HINT))
		TextInput(
			text(Text.USERNAME), username, { username = it }, tag = "username", icon = AppIcon.USER, contentType = ContentType.Username,
			error = required.takeIf { tried && username.isBlank() }, onFocusChanged = onFieldFocus,
		)
		TextInput(
			text(Text.PASSWORD), password, { password = it }, tag = "password", icon = AppIcon.LOCK, password = true, contentType = ContentType.Password,
			error = required.takeIf { tried && password.isEmpty() }, imeAction = ImeAction.Done, onDone = submit, onFocusChanged = onFieldFocus,
		)
		if (state.error != null) AppText(state.error, StockFlowType.bodyMd, StockFlow.colors.danger)
		PrimaryButton(text(if (state.submitting) Text.SIGNING_IN else Text.SIGN_IN), onClick = submit, tag = "sign-in", busy = state.submitting)
	}
}

@Composable
fun ChangePasswordScreen(state: SessionState.PasswordChange, onChange: (String, String) -> Unit) {
	var current by rememberSaveable { mutableStateOf("") }
	var new by rememberSaveable { mutableStateOf("") }
	var tried by rememberSaveable { mutableStateOf(false) }
	val (scrollState, onFieldFocus) = rememberButtonScroll()
	val required = text(Text.FIELD_REQUIRED)
	val submit = {
		tried = true
		if (current.isNotEmpty() && new.isNotEmpty() && !state.submitting) onChange(current, new)
	}
	EntryScreen(Text.CHANGE_PASSWORD_HEADLINE, scrollState) {
		BentoSectionTitle(text(Text.CHANGE_PASSWORD_TITLE), text(Text.CHANGE_PASSWORD_HINT))
		TextInput(
			text(Text.CURRENT_PASSWORD), current, { current = it }, tag = "current-password", icon = AppIcon.LOCK, password = true, contentType = ContentType.Password,
			error = state.fieldErrors["currentPassword"] ?: required.takeIf { tried && current.isEmpty() }, onFocusChanged = onFieldFocus,
		)
		TextInput(
			text(Text.NEW_PASSWORD), new, { new = it }, tag = "new-password", icon = AppIcon.LOCK, password = true, contentType = ContentType.NewPassword,
			error = state.fieldErrors["newPassword"] ?: required.takeIf { tried && new.isEmpty() }, imeAction = ImeAction.Done, onDone = submit, onFocusChanged = onFieldFocus,
		)
		if (state.error != null) AppText(state.error, StockFlowType.bodyMd, StockFlow.colors.danger)
		PrimaryButton(text(Text.SAVE_PASSWORD), onClick = submit, tag = "save-password", busy = state.submitting)
	}
}
