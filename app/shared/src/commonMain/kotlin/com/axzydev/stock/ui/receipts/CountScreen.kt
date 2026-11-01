package com.axzydev.stock.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.receipts.AssignmentView
import com.axzydev.stock.feature.receipts.CountState
import com.axzydev.stock.feature.receipts.CountTarget
import com.axzydev.stock.feature.receipts.CountViewModel
import com.axzydev.stock.feature.receipts.PlacementTarget
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoChip
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
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.riseIn
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Counting what arrived (docs/modules/material-receiving.md section 4, barcode-scanning.md section 3). The operator scans
// the material, or writes in the search box over their own list of what is left to count; the app opens the assignment
// and takes the counted quantity, which starts at what was expected. A material with no open assignment says so.
@Composable
fun CountScreen(count: CountViewModel, onBack: () -> Unit, onPlace: (PlacementTarget) -> Unit) {
	val state by count.state.collectAsState()
	LaunchedEffect(state.target) { if (state.target == null) count.load() }
	PageScaffold(
		title = text(Text.PAGE_COUNT),
		subtitle = if (state.target == null) text(Text.PAGE_COUNT_HINT) else null,
		onBack = if (state.target != null && state.saved == null) count::clear else onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "count-back",
	) {
		when {
			state.saved != null -> CountResult(state.target!!, state.saved!!, count::clear, onPlace)

			state.target != null -> CountForm(state, state.target!!, count)

			else -> ScanSearch(
				instruction = text(Text.SCAN_MATERIAL),
				searchLabel = text(Text.SEARCH_MATERIAL),
				query = state.query,
				onQueryChange = count::edit,
				onScanned = count::scanned,
				onSubmit = count::submit,
				tag = "count-value",
				error = state.fieldError,
				searching = state.looking,
			) {
				state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("count-error")) }
				// Several materials matched the name written: the operator picks one before its assignments show.
				if (state.products.isNotEmpty()) {
					BentoLabel(text(Text.LOOKUP_CHOOSE).replace("{count}", state.products.size.toString()))
					state.products.forEach { product ->
						BentoChoiceRow(product.name, "count-material-${product.code}", { count.choose(product) }, subtitle = product.code, icon = AppIcon.BOXES)
					}
				}
				// The material is in several open assignments: the operator picks the one they are counting.
				if (state.targets.isNotEmpty()) {
					BentoLabel(text(Text.COUNT_CHOOSE_ASSIGNMENT))
					state.targets.forEach { target -> TargetRow(target, count) }
				}
				if (state.products.isEmpty() && state.targets.isEmpty()) Queue(state, count)
			}
		}
	}
}

// What is left to count, narrowed by what is written, so a person without a scanner never needs the exact code.
@Composable
private fun Queue(state: CountState, count: CountViewModel) {
	val shown = state.queue.filter { state.query.matches(it.product.code, it.product.name, it.invoice) }
	when {
		state.loadingQueue -> Unit
		state.queue.isEmpty() -> BentoState(text(Text.EMPTY_COUNT), "count-empty", text(Text.EMPTY_HINT))
		shown.isEmpty() -> BentoState(text(Text.NO_RESULTS), "count-no-results", text(Text.NO_RESULTS_HINT), AppIcon.SEARCH)
		else -> {
			BentoLabel("${text(Text.QUEUE_COUNT)} · ${shown.size}")
			shown.forEachIndexed { index, target ->
				Column(Modifier.riseIn(index)) { TargetRow(target, count) }
			}
		}
	}
}

@Composable
private fun TargetRow(target: CountTarget, count: CountViewModel) {
	BentoChoiceRow(
		title = lineTitle(target),
		subtitle = target.invoice,
		detail = expectedText(target),
		tag = "count-assignment-${target.assignment.id}",
		onClick = { count.choose(target) },
	)
}

// The count itself: what was expected, in big, and the quantity, which starts there: most counts only confirm it.
@Composable
private fun CountForm(state: CountState, target: CountTarget, count: CountViewModel) {
	var writingNote by rememberSaveable { mutableStateOf(state.notes.isNotEmpty()) }
	BentoHero(lineTitle(target), expectedText(target), text(Text.EXPECTED), "count-target", subtitle = target.invoice)
	BentoQuantity(text(Text.COUNT_QUANTITY), state.quantity, count::editQuantity, "count-quantity", state.quantityError, onDone = count::save)
	if (writingNote) {
		TextInput(label = text(Text.COUNT_NOTES), value = state.notes, onValueChange = count::editNotes, tag = "count-notes")
	} else {
		BentoChip(text(Text.ADD_NOTE), selected = false, tag = "count-add-note") { writingNote = true }
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("count-error")) }
	PrimaryButton(text(Text.COUNT_SAVE), count::save, tag = "count-save", busy = state.saving, icon = AppIcon.CHECK)
}

// A settled count goes straight to placement; an open difference still lets the operator continue with other lines.
@Composable
private fun CountResult(target: CountTarget, saved: AssignmentView, onAnother: () -> Unit, onPlace: (PlacementTarget) -> Unit) {
	val matched = saved.outcome == MATCH
	BentoResult(text(if (matched) Text.COUNT_MATCH else Text.COUNT_DISCREPANCY), "count-saved", success = matched) {
		AppText(lineTitle(target), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
	}
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		if (saved.readyToPlace) {
			PrimaryButton(
				text = text(Text.COUNT_PLACE_NOW),
				onClick = { onPlace(PlacementTarget(saved, target.invoice, target.warehouseId, target.product, target.unit)) },
				tag = "count-place-now",
				icon = AppIcon.WAREHOUSE,
			)
			SecondaryButton(text(Text.COUNT_ANOTHER), onAnother, tag = "count-another", icon = AppIcon.SCAN)
		} else {
			PrimaryButton(text(Text.COUNT_ANOTHER), onAnother, tag = "count-another", icon = AppIcon.SCAN)
		}
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
