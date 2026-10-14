package com.axzydev.stock.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.receipts.AssignmentView
import com.axzydev.stock.feature.receipts.CountState
import com.axzydev.stock.feature.receipts.CountTarget
import com.axzydev.stock.feature.receipts.CountViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.ScannerPanel
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Counting what arrived (docs/modules/material-receiving.md section 4, barcode-scanning.md section 3). The operator
// scans the material; the app finds their open assignment of it and takes the counted quantity. A material with no
// open assignment says so, and nothing is counted.
@Composable
fun CountScreen(count: CountViewModel, onBack: () -> Unit) {
	val state by count.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	val search = {
		focus.clearFocus()
		keyboard?.hide()
		count.submit()
	}
	PageScaffold(
		title = text(Text.PAGE_COUNT),
		subtitle = text(Text.PAGE_COUNT_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "count-back",
	) {
		when {
			state.saved != null -> CountResult(state.saved!!, count::clear)

			state.target != null -> CountForm(state, state.target!!, count)

			else -> {
				ScannerPanel(onScanned = count::scanned)
				TextInput(
					label = text(Text.LOOKUP_FIELD),
					value = state.query,
					onValueChange = count::edit,
					tag = "count-value",
					icon = AppIcon.SEARCH,
					error = state.fieldError,
					imeAction = ImeAction.Search,
					onDone = search,
				)
				PrimaryButton(
					text = text(if (state.looking) Text.LOOKUP_SEARCHING else Text.LOOKUP_SEARCH),
					onClick = search,
					tag = "count-search",
					busy = state.looking,
				)
				state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("count-error")) }
				// Several materials matched the name written: the operator picks one before its assignments show.
				if (state.products.isNotEmpty()) {
					AppText(text(Text.LOOKUP_CHOOSE).replace("{count}", state.products.size.toString()), StockFlowType.bodyLg, StockFlow.colors.onSurface)
					state.products.forEach { product ->
						Pick(
							title = product.name,
							subtitle = product.code,
							tag = "count-material-${product.code}",
							onClick = { count.choose(product) },
						)
					}
				}
				// The material is in several open assignments: the operator picks the one they are counting.
				if (state.targets.isNotEmpty()) {
					AppText(text(Text.COUNT_CHOOSE_ASSIGNMENT), StockFlowType.bodyLg, StockFlow.colors.onSurface)
					state.targets.forEach { target ->
						Pick(
							title = lineTitle(target),
							subtitle = target.invoice,
							tag = "count-assignment-${target.assignment.id}",
							onClick = { count.choose(target) },
						)
					}
				}
			}
		}
	}
}

// The count itself: what was expected, the quantity the operator counted, and an optional note.
@Composable
private fun CountForm(state: CountState, target: CountTarget, count: CountViewModel) {
	Card(Modifier.testTag("count-target")) {
		AppText(lineTitle(target), StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		AppText(target.invoice, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		Field(text(Text.EXPECTED), expectedText(target))
	}
	TextInput(
		label = text(Text.COUNT_QUANTITY),
		value = state.quantity,
		onValueChange = count::editQuantity,
		tag = "count-quantity",
		numeric = true,
		error = state.quantityError,
		imeAction = ImeAction.Done,
		onDone = count::save,
	)
	TextInput(
		label = text(Text.COUNT_NOTES),
		value = state.notes,
		onValueChange = count::editNotes,
		tag = "count-notes",
	)
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("count-error")) }
	PrimaryButton(text(Text.COUNT_SAVE), count::save, tag = "count-save", busy = state.saving)
}

// What the API answered about the count: a match, or a difference the office has to adjust.
@Composable
private fun CountResult(saved: AssignmentView, onAnother: () -> Unit) {
	val matched = saved.outcome == MATCH
	Card(Modifier.testTag("count-saved")) {
		AppText(
			text(if (matched) Text.COUNT_MATCH else Text.COUNT_DISCREPANCY),
			StockFlowType.bodyLg,
			if (matched) StockFlow.colors.success else StockFlow.colors.warning,
		)
	}
	PrimaryButton(text(Text.COUNT_ANOTHER), onAnother, tag = "count-another")
}

// One choice of a list: a title, a line under it, and a tap that picks it.
@Composable
private fun Pick(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
	val shape = RoundedCornerShape(DesignTokens.Components.cardPanel.rounded.dp)
	Column(
		Modifier
			.fillMaxWidth()
			.background(Color(DesignTokens.Components.cardPanel.backgroundColor), shape)
			.border(1.dp, StockFlow.colors.outline, shape)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.md.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp),
	) {
		AppText(title, StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 2)
		AppText(subtitle, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
	}
}

@Composable
private fun Field(label: String, value: String) {
	Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		AppText(label.uppercase(), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
		AppText(value, StockFlowType.bodyLg, StockFlow.colors.onSurface)
	}
}

// The material of an assignment: its code and name.
private fun lineTitle(target: CountTarget): String = listOfNotNull(target.product.code, target.product.name).joinToString(" · ")

// The expected quantity with the unit it is counted in, when the unit is known.
private fun expectedText(target: CountTarget): String {
	val quantity = formatQuantity(target.assignment.expectedQuantity, target.unit?.decimalPlaces)
	val unit = target.unit?.label ?: target.unit?.code
	return if (unit == null) quantity else "$quantity $unit"
}

private const val MATCH = "MATCH"
