package com.axzydev.stock.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.axzydev.stock.feature.receipts.PlacementLine
import com.axzydev.stock.feature.receipts.PlacementState
import com.axzydev.stock.feature.receipts.PlacementTarget
import com.axzydev.stock.feature.receipts.PlacementViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoChoiceRow
import com.axzydev.stock.ui.components.BentoHero
import com.axzydev.stock.ui.components.BentoLabel
import com.axzydev.stock.ui.components.BentoQuantity
import com.axzydev.stock.ui.components.BentoResult
import com.axzydev.stock.ui.components.BentoState
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.ScanSearch
import com.axzydev.stock.ui.components.SecondaryButton
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.riseIn
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Putting counted goods into locations (docs/modules/material-receiving.md section 7, barcode-scanning.md section 4). The
// operator scans or finds the material in their queue, then scans each location it goes to, or finds it in the search
// box. The quantity of a location starts at what is left, and the app keeps the sum at or below what was counted.
@Composable
fun PlacementScreen(placement: PlacementViewModel, onBack: () -> Unit, onNext: () -> Unit) {
	val state by placement.state.collectAsState()

	// A target opened directly from a count is kept; a fresh page loads the work queue.
	LaunchedEffect(placement) {
		if (state.target == null && state.saved == null) placement.load()
	}

	PageScaffold(
		title = text(Text.PAGE_PLACEMENT),
		subtitle = if (state.target == null) text(Text.PAGE_PLACEMENT_HINT) else null,
		onBack = if (state.target != null) placement::back else onBack,
		backLabel = if (state.target != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "placement-back",
	) {
		when {
			state.saved != null -> PlacementResult(state.target, state.saved!!.placed, placement::continuePlacement, onNext)

			state.target != null -> PlacementForm(state, placement)

			else -> ScanSearch(
				instruction = text(Text.SCAN_MATERIAL),
				searchLabel = text(Text.SEARCH_MATERIAL),
				query = state.queueQuery,
				onQueryChange = placement::editQueueQuery,
				onScanned = placement::scannedMaterial,
				onSubmit = placement::submitQueueQuery,
				tag = "placement-material",
				error = state.queueError,
			) { Queue(state, placement) }
		}
	}
}

// What is left to place, narrowed by what is written.
@Composable
private fun Queue(state: PlacementState, placement: PlacementViewModel) {
	val shown = state.targets.filter { state.queueQuery.matches(it.product.code, it.product.name, it.invoice) }
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-error")) }
	when {
		state.loading -> Unit
		state.targets.isEmpty() -> BentoState(text(Text.EMPTY_PLACE), "placement-empty", text(Text.EMPTY_HINT))
		shown.isEmpty() -> BentoState(text(Text.NO_RESULTS), "placement-no-results", text(Text.NO_RESULTS_HINT), AppIcon.SEARCH)
		else -> {
			BentoLabel("${text(Text.QUEUE_PLACE)} · ${shown.size}")
			shown.forEachIndexed { index, target ->
				Column(Modifier.riseIn(index)) {
					BentoChoiceRow(lineTitle(target), "placement-target-${target.assignment.id}", { placement.open(target) }, subtitle = target.invoice, detail = countedText(target))
				}
			}
		}
	}
}

@Composable
private fun PlacementForm(state: PlacementState, placement: PlacementViewModel) {
	val target = state.target ?: return
	// What no row has claimed yet, from the total that is left to place.
	val left = state.unassigned ?: state.remaining
	BentoHero(lineTitle(target), formatQuantity(left, target.unit?.decimalPlaces), text(Text.QUEUE_PLACE), "placement-target", subtitle = target.invoice)

	ScanSearch(
		instruction = text(Text.SCAN_LOCATION),
		searchLabel = text(Text.SEARCH_LOCATION),
		query = state.scan,
		onQueryChange = placement::editScan,
		onScanned = placement::scanned,
		onSubmit = placement::addLocation,
		tag = "placement-scan",
		error = state.scanError,
		onSearch = placement::suggest,
	) {
		state.suggestions.forEach { location ->
			BentoChoiceRow(location.code, "placement-suggestion-${location.code}", { placement.chooseLocation(location) }, icon = AppIcon.WAREHOUSE)
		}
	}

	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-error")) }
	state.lineError?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("placement-line-error")) }

	if (state.lines.isNotEmpty()) {
		BentoLabel(text(Text.PLACEMENT_CHOSEN))
		state.lines.forEachIndexed { index, line ->
			PlacementRow(
				line = line,
				index = index,
				onQuantityChange = { placement.editQuantity(index, it) },
				onRemove = { placement.remove(index) },
			)
		}
		PrimaryButton(text(Text.PLACEMENT_SAVE), placement::save, tag = "placement-save", busy = state.saving, icon = AppIcon.CHECK)
	}
}

// One location the material goes to, and how much: the quantity starts at what was left when it was added.
@Composable
private fun PlacementRow(line: PlacementLine, index: Int, onQuantityChange: (String) -> Unit, onRemove: () -> Unit) {
	Column(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, RoundedCornerShape(AppBento.tile))
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag("placement-line-$index"),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp),
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			AppText(line.locationCode, StockFlowType.titleMd, StockFlow.colors.onSurface, Modifier.weight(1f), maxLines = 1)
			Box(
				Modifier
					.size(36.dp)
					.background(StockFlow.colors.surfaceSoft, CircleShape)
					.clickable(role = Role.Button, onClick = onRemove)
					.semantics { contentDescription = "remove-$index" }
					.testTag("placement-remove-$index"),
				contentAlignment = Alignment.Center,
			) {
				AppText("×", StockFlowType.titleMd, StockFlow.colors.danger, maxLines = 1)
			}
		}
		BentoQuantity(text(Text.COUNT_QUANTITY), line.quantity, onQuantityChange, "placement-quantity-$index")
	}
}

@Composable
private fun PlacementResult(target: PlacementTarget?, complete: Boolean, onContinue: () -> Unit, onNext: () -> Unit) {
	BentoResult(text(Text.PLACED), "placement-saved") {
		if (target != null) AppText(lineTitle(target), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
	}
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		if (!complete) {
			PrimaryButton(text(Text.PLACEMENT_CONTINUE), onContinue, tag = "placement-continue", icon = AppIcon.WAREHOUSE)
			SecondaryButton(text(Text.NEXT_MATERIAL), onNext, tag = "placement-next", icon = AppIcon.SCAN)
		} else {
			PrimaryButton(text(Text.NEXT_MATERIAL), onNext, tag = "placement-next", icon = AppIcon.SCAN)
		}
	}
}

// The material of a target: its code and name.
private fun lineTitle(target: PlacementTarget): String = listOfNotNull(target.product.code, target.product.name).joinToString(" · ")

private fun countedText(target: PlacementTarget): String {
	val counted = formatQuantity(target.assignment.countedQuantity.orEmpty(), target.unit?.decimalPlaces)
	val unit = target.unit?.label ?: target.unit?.code
	return if (unit == null) counted else "$counted $unit"
}
