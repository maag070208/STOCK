package com.axzydev.stock.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.feature.receipts.PlacementLine
import com.axzydev.stock.feature.receipts.PlacementState
import com.axzydev.stock.feature.receipts.PlacementTarget
import com.axzydev.stock.feature.receipts.PlacementViewModel
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

// Putting counted goods into locations (docs/modules/material-receiving.md section 7, barcode-scanning.md section 4). The
// operator picks a counted assignment, scans the location for each part, and the app keeps the sum equal to what is left.
@Composable
fun PlacementScreen(placement: PlacementViewModel, onBack: () -> Unit) {
	val state by placement.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current

	// Load the list of what is still to place when entering that page.
	LaunchedEffect(placement) { placement.load() }

	val search = {
		focus.clearFocus()
		keyboard?.hide()
		placement.addLocation()
	}

	PageScaffold(
		title = text(Text.PAGE_PLACEMENT),
		subtitle = text(Text.PAGE_PLACEMENT_HINT),
		onBack = if (state.target != null) placement::back else onBack,
		backLabel = if (state.target != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "placement-back",
	) {
		when {
			state.saved != null -> PlacementResult(onBack, placement::load)

			state.target != null -> PlacementForm(state, placement, search)

			else -> {
				state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-error")) }
				if (state.loading) AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
				if (state.targets.isEmpty() && !state.loading) AppText(text(Text.EMPTY), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
				state.targets.forEach { target ->
					PickPlacement(
						target = target,
						tag = "placement-target-${target.assignment.id}",
						onClick = { placement.open(target) },
					)
				}
			}
		}
	}
}

@Composable
private fun PickPlacement(target: PlacementTarget, tag: String, onClick: () -> Unit) {
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
		AppText(lineTitle(target), StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 2)
		AppText(target.invoice, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
		val counted = formatQuantity(target.assignment.countedQuantity.orEmpty(), target.unit?.decimalPlaces)
		val unit = target.unit?.label ?: target.unit?.code
		AppText(if (unit == null) counted else "$counted $unit", StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
	}
}

@Composable
private fun PlacementForm(state: PlacementState, placement: PlacementViewModel, search: () -> Unit) {
	val target = state.target ?: return
	Card(Modifier.testTag("placement-target")) {
		AppText(lineTitle(target), StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		AppText(target.invoice, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		val counted = formatQuantity(target.assignment.countedQuantity.orEmpty(), target.unit?.decimalPlaces)
		val unit = target.unit?.label ?: target.unit?.code
		AppText(if (unit == null) counted else "$counted $unit", StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		// What no row has claimed yet, from the total that is left to place.
		state.unassigned?.let { left ->
			val remaining = text(Text.PLACEMENT_REMAINING).replace("{quantity}", formatQuantity(left, target.unit?.decimalPlaces))
			AppText(remaining, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		}
	}

	ScannerPanel(onScanned = placement::scanned)
	TextInput(
		label = text(Text.PLACEMENT_SCAN),
		value = state.scan,
		onValueChange = placement::editScan,
		tag = "placement-scan",
		icon = AppIcon.SEARCH,
		error = state.scanError,
		imeAction = ImeAction.Search,
		onDone = search,
	)
	PrimaryButton(text(Text.PLACEMENT_ADD), placement::addLocation, tag = "placement-add")

	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-error")) }
	state.lineError?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-line-error")) }

	if (state.lines.isNotEmpty()) {
		state.lines.forEachIndexed { index, line ->
			PlacementRow(
				line = line,
				index = index,
				onQuantityChange = { placement.editQuantity(index, it) },
				onRemove = { placement.remove(index) },
			)
		}
		PrimaryButton(text(Text.PLACEMENT_SAVE), placement::save, tag = "placement-save", busy = state.saving)
	}
}

@Composable
private fun PlacementRow(line: PlacementLine, index: Int, onQuantityChange: (String) -> Unit, onRemove: () -> Unit) {
	Card(Modifier.testTag("placement-line-$index")) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			AppText(line.locationCode, StockFlowType.titleMd, StockFlow.colors.onSurface, Modifier.weight(1f), maxLines = 1)
			Spacer(Modifier.width(DesignTokens.Spacing.md.dp))
			TextInput(
				label = text(Text.COUNT_QUANTITY),
				value = line.quantity,
				onValueChange = onQuantityChange,
				tag = "placement-quantity-$index",
				modifier = Modifier.width(180.dp),
				numeric = true,
				imeAction = ImeAction.Done,
			)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			Remove(text(Text.PLACEMENT_REMOVE), onRemove, "placement-remove-$index")
		}
	}
}

// A small destructive action, drawn as text: the design has no ghost button, so "quitar" is quiet text in the danger color.
@Composable
private fun Remove(label: String, onClick: () -> Unit, tag: String) {
	Box(
		Modifier
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = DesignTokens.Spacing.md.dp, vertical = DesignTokens.Spacing.xs.dp)
			.testTag(tag),
		contentAlignment = Alignment.Center,
	) {
		AppText(label, StockFlowType.bodyMd, StockFlow.colors.danger)
	}
}

@Composable
private fun PlacementResult(onBack: () -> Unit, onAnother: () -> Unit) {
	Card(Modifier.testTag("placement-saved")) {
		AppText(text(Text.PLACED), StockFlowType.bodyLg, StockFlow.colors.success)
	}
	PrimaryButton(text(Text.PLACEMENT_ANOTHER), onAnother, tag = "placement-another")
	PrimaryButton(text(Text.BACK_MODULES), onBack, tag = "placement-done")
}

// The material of a target: its code and name.
private fun lineTitle(target: PlacementTarget): String = listOfNotNull(target.product.code, target.product.name).joinToString(" · ")