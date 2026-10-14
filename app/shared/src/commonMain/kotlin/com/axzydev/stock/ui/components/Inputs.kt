package com.axzydev.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.theme.AppLayout
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType
import com.axzydev.stock.ui.theme.toTextStyle

// A field as the reference draws it: a gray pill with an icon, and the label written inside while it is empty.
// It has no border until it has the cursor (orange) or an error (red, written below). A password field has an eye
// to show it. `contentType` lets the phone's password manager fill it. Screen readers hear the label.
@Composable
fun TextInput(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	tag: String,
	modifier: Modifier = Modifier,
	icon: AppIcon? = null,
	password: Boolean = false,
	/** A quantity or a code read as a number: the phone shows its numeric keyboard. */
	numeric: Boolean = false,
	contentType: ContentType? = null,
	error: String? = null,
	imeAction: ImeAction = ImeAction.Next,
	onDone: () -> Unit = {},
	onFocusChanged: ((Boolean) -> Unit)? = null,
) {
	val colors = StockFlow.colors
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	var visible by rememberSaveable { mutableStateOf(false) }
	val border = when {
		error != null -> colors.danger
		focused -> colors.primaryFocus
		else -> Color.Transparent
	}
	Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			singleLine = true,
			textStyle = StockFlowType.bodyLg.copy(color = colors.onSurface),
			cursorBrush = SolidColor(colors.primaryFocus),
			visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
			keyboardOptions = KeyboardOptions(
				keyboardType = when {
					password -> KeyboardType.Password
					numeric -> KeyboardType.Decimal
					else -> KeyboardType.Ascii
				},
				imeAction = imeAction,
				autoCorrectEnabled = false,
			),
			// The keyboard's last key sends the form, whether it reads Done or Search.
			keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }),
			interactionSource = interaction,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = AppLayout.pillHeight)
				.background(colors.surface, AppLayout.pill)
				.border(2.dp, border, AppLayout.pill)
				.onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
				.semantics {
					contentDescription = label
					if (contentType != null) this.contentType = contentType
				}
				.testTag(tag),
			decorationBox = { field ->
				Row(
					Modifier.heightIn(min = AppLayout.pillHeight).padding(start = DesignTokens.Spacing.lg.dp + DesignTokens.Spacing.xs.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					if (icon != null) {
						Icon(icon, colors.onSurfaceMuted)
						Spacer(Modifier.width(DesignTokens.Spacing.md.dp))
					}
					Box(Modifier.weight(1f).padding(end = DesignTokens.Spacing.lg.dp)) {
						if (value.isEmpty()) AppText(label, StockFlowType.bodyLg, colors.onSurfaceMuted, maxLines = 1)
						field()
					}
					if (password) {
						val action = text(if (visible) Text.HIDE_PASSWORD else Text.SHOW_PASSWORD)
						Box(
							Modifier
								.heightIn(min = AppLayout.pillHeight)
								// The keyboard's Next key skips it and goes to the next field. It is still a button to tap and to screen readers.
								.focusProperties { canFocus = false }
								.clickable(role = Role.Button) { visible = !visible }
								.semantics { contentDescription = action }
								.padding(horizontal = DesignTokens.Spacing.lg.dp)
								.testTag("$tag-visibility"),
							contentAlignment = Alignment.Center,
						) {
							Icon(if (visible) AppIcon.EYE_OFF else AppIcon.EYE, colors.onSurfaceMuted)
						}
					}
				}
			},
		)
		if (error != null) AppText(error, StockFlowType.bodyMd, colors.danger, Modifier.padding(start = DesignTokens.Spacing.lg.dp))
	}
}

private val primary = DesignTokens.Components.buttonPrimary
private val primaryPressed = DesignTokens.Components.buttonPrimaryHover

// The design's primary button in the reference's shape: a pill of saturated orange with white text, the stronger
// orange when pressed. While its action runs it keeps its color, shows what it is doing and ignores more taps.
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, busy: Boolean = false, icon: AppIcon? = null) {
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val background = Color(if (pressed || busy) primaryPressed.backgroundColor else primary.backgroundColor)
	val content = Color(primary.textColor)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = AppLayout.pillHeight)
			.background(background, AppLayout.pill)
			.clickable(interactionSource = interaction, indication = null, enabled = !busy, role = Role.Button, onClick = onClick)
			.padding(horizontal = DesignTokens.Spacing.xl.dp)
			.testTag(tag),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		if (icon != null) {
			Icon(icon, content)
			Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
		}
		AppText(text, requireNotNull(primary.typography).toTextStyle().copy(fontWeight = StockFlowType.titleMd.fontWeight), content, maxLines = 1)
	}
}
