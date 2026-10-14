package com.axzydev.stock.ui.signin

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.feature.session.SessionState
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.HeroBar
import com.axzydev.stock.ui.components.HeroHeadline
import com.axzydev.stock.ui.components.HeroScreen
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.SheetTitle
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowTheme
import com.axzydev.stock.ui.theme.StockFlowType

// The entry screens: the orange hero with the brand and a headline, and the form on the sheet below.
// They are the brand moment, so they keep the light look in dark mode too.
@Composable
private fun EntryScreen(headline: Text, form: @Composable () -> Unit) {
	StockFlowTheme(dark = false) {
		HeroScreen(
			art = true,
			hero = {
				HeroBar()
				HeroHeadline(text(headline), Modifier.padding(top = DesignTokens.Spacing.xl.dp, end = DesignTokens.Spacing.xl3.dp))
			},
		) { form() }
	}
}

@Composable
fun SignInScreen(state: SessionState.SignedOut, onSignIn: (String, String) -> Unit) {
	var username by rememberSaveable { mutableStateOf("") }
	var password by rememberSaveable { mutableStateOf("") }
	var tried by rememberSaveable { mutableStateOf(false) }
	val required = text(Text.FIELD_REQUIRED)
	val submit = {
		tried = true
		if (username.isNotBlank() && password.isNotEmpty() && !state.submitting) onSignIn(username, password)
	}
	EntryScreen(Text.SIGN_IN_HEADLINE) {
		SheetTitle(text(Text.SIGN_IN_TITLE), text(Text.SIGN_IN_HINT))
		TextInput(
			text(Text.USERNAME), username, { username = it }, tag = "username", icon = AppIcon.USER, contentType = ContentType.Username,
			error = required.takeIf { tried && username.isBlank() },
		)
		TextInput(
			text(Text.PASSWORD), password, { password = it }, tag = "password", icon = AppIcon.LOCK, password = true, contentType = ContentType.Password,
			error = required.takeIf { tried && password.isEmpty() }, imeAction = ImeAction.Done, onDone = submit,
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
	val required = text(Text.FIELD_REQUIRED)
	val submit = {
		tried = true
		if (current.isNotEmpty() && new.isNotEmpty() && !state.submitting) onChange(current, new)
	}
	EntryScreen(Text.CHANGE_PASSWORD_HEADLINE) {
		SheetTitle(text(Text.CHANGE_PASSWORD_TITLE), text(Text.CHANGE_PASSWORD_HINT))
		TextInput(
			text(Text.CURRENT_PASSWORD), current, { current = it }, tag = "current-password", icon = AppIcon.LOCK, password = true, contentType = ContentType.Password,
			error = state.fieldErrors["currentPassword"] ?: required.takeIf { tried && current.isEmpty() },
		)
		TextInput(
			text(Text.NEW_PASSWORD), new, { new = it }, tag = "new-password", icon = AppIcon.LOCK, password = true, contentType = ContentType.NewPassword,
			error = state.fieldErrors["newPassword"] ?: required.takeIf { tried && new.isEmpty() }, imeAction = ImeAction.Done, onDone = submit,
		)
		if (state.error != null) AppText(state.error, StockFlowType.bodyMd, StockFlow.colors.danger)
		PrimaryButton(text(Text.SAVE_PASSWORD), onClick = submit, tag = "save-password", busy = state.submitting)
	}
}
