package com.axzydev.stock.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.orders.OrderAlert
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.feature.orders.OrderReassignmentState
import com.axzydev.stock.feature.orders.OrderReassignmentViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoChip
import com.axzydev.stock.ui.components.BentoChoiceRow
import com.axzydev.stock.ui.components.BentoResult
import com.axzydev.stock.ui.components.BentoSkeleton
import com.axzydev.stock.ui.components.BentoState
import com.axzydev.stock.ui.components.BentoLabel
import com.axzydev.stock.ui.components.BentoSteps
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.Icon
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.ScanSearch
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.riseIn
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Reassigning picked material (docs/modules/order-fulfillment.md section 8, barcode-scanning.md section 4). The person
// opens the alert of an order that is falling due, chooses (or scans) the material that was already picked, touches the
// order that needs it, and confirms: the quantity is already the most that can move and the note is one touch. No stock
// changes. Back goes one step back.
@Composable
fun ReassignmentScreen(reassign: OrderReassignmentViewModel, onBack: () -> Unit) {
	val state by reassign.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	LaunchedEffect(reassign) { reassign.load() }

	val finding = { reassign.submitScan() }
	val saving = {
		focus.clearFocus()
		keyboard?.hide()
		reassign.save()
	}

	PageScaffold(
		title = text(Text.PAGE_REASSIGNMENT),
		subtitle = if (state.order == null) text(Text.PAGE_REASSIGNMENT_HINT) else null,
		onBack = if (state.order != null) reassign::back else onBack,
		backLabel = if (state.order != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "reassign-back",
	) {
		when {
			state.order == null -> AlertList(state, reassign)

			state.saved != null -> ReassignmentResult(state, reassign)

			// The order is read again with its lines before anything is chosen.
			state.loading -> BentoSkeleton(tag = "reassign-loading")

			else -> ReassignmentForm(state, reassign, finding, saving)
		}
	}
}

// The alerts of the signed-in person: the orders that are due tomorrow, today, or past their date.
@Composable
private fun AlertList(state: OrderReassignmentState, reassign: OrderReassignmentViewModel) {
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
	if (state.loading) BentoSkeleton(tag = "reassign-loading")
	if (state.alerts.isEmpty() && !state.loading) AppText(text(Text.EMPTY), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
	if (state.alerts.isNotEmpty()) BentoLabel(text(Text.REASSIGN_ALERTS))
	state.alerts.forEachIndexed { index, alert ->
		Column(Modifier.riseIn(index)) {
			// The kind of the alert, in the language of the person reading it.
			BentoChoiceRow(
				title = alert.order.number,
				subtitle = "${text(Text.PICK_DUE).replace("{date}", alert.order.dueDate)} · ${alert.order.customer.name ?: alert.order.customer.code.orEmpty()}",
				detail = text(alertKind(alert.kind)),
				tone = alertTone(alert.kind),
				tag = "alert-${alert.order.number}",
				onClick = { reassign.open(alert) },
			)
		}
	}
}

// The three steps of the move, with what was already decided kept in sight.
@Composable
private fun ReassignmentForm(
	state: OrderReassignmentState,
	reassign: OrderReassignmentViewModel,
	finding: () -> Unit,
	saving: () -> Unit,
) {
	val order = state.order ?: return
	val line = state.line
	val target = state.target
	BentoSteps(
		listOf(text(Text.REASSIGN_STEP_MATERIAL), text(Text.REASSIGN_STEP_TARGET), text(Text.REASSIGN_STEP_QUANTITY)),
		current = if (line == null) 0 else if (target == null) 1 else 2,
	)
	Card(Modifier.testTag("reassign-order")) {
		AppText(order.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			"${order.customer.name ?: order.customer.code.orEmpty()} · ${order.warehouse.code.orEmpty()} · ${order.status.label ?: order.status.code}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 1,
		)
		if (line != null) {
			Spacer(Modifier.height(DesignTokens.Spacing.xs.dp))
			AppText(lineTitle(line), StockFlowType.labelMd, StockFlow.colors.onSurface, Modifier.testTag("reassign-line"), maxLines = 2)
			AppText(
				text(Text.REASSIGN_PICKED).replace("{quantity}", formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces)),
				StockFlowType.titleMd,
				StockFlow.colors.primary,
				Modifier.testTag("reassign-picked"),
			)
		}
	}

	when {
		line == null -> MaterialStep(state, reassign, finding)
		target == null -> TargetStep(state, reassign)
		else -> QuantityStep(state, reassign, saving)
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
}

// The material: scan it, or find it among what the order picked, narrowed by what is written.
@Composable
private fun MaterialStep(state: OrderReassignmentState, reassign: OrderReassignmentViewModel, finding: () -> Unit) {
	val picked = state.order?.lines.orEmpty().filter { Decimals.above(it.pickedQuantity, "0") }
	val shown = picked.filter { state.scan.matches(it.product.code, it.product.name) }
	ScanSearch(
		instruction = text(Text.SCAN_MATERIAL),
		searchLabel = text(Text.REASSIGN_SCAN),
		query = state.scan,
		onQueryChange = reassign::editScan,
		onScanned = reassign::scanned,
		onSubmit = finding,
		tag = "reassign-scan",
		error = state.fieldError ?: state.scanError,
	) {
		if (picked.isEmpty()) {
			BentoState(text(Text.REASSIGN_NONE_PICKED), "reassign-none-picked", icon = AppIcon.BOXES)
		} else {
			BentoLabel(text(Text.REASSIGN_LINES))
			shown.forEach { line ->
				BentoChoiceRow(
					title = lineTitle(line),
					detail = formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces),
					tag = "reassign-pick-${line.product.code}",
					onClick = { reassign.selectLine(line) },
				)
			}
		}
	}
}

// The open orders of the same warehouse that need the material: one touch chooses.
@Composable
private fun TargetStep(state: OrderReassignmentState, reassign: OrderReassignmentViewModel) {
	BentoLabel(text(Text.REASSIGN_TARGETS))
	if (state.targets.isEmpty()) AppText(text(Text.REASSIGN_NO_TARGETS), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
	state.targetError?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-target-error")) }
	state.targets.forEachIndexed { index, target ->
		Column(Modifier.riseIn(index)) {
			BentoChoiceRow(
				title = target.order.number,
				subtitle = target.order.customer.name ?: target.order.customer.code.orEmpty(),
				detail = target.line.remaining?.let { text(Text.REASSIGN_NEEDS).replace("{quantity}", formatQuantity(it, target.line.unit?.decimalPlaces)) },
				tag = "reassign-target-${target.order.number}",
				onClick = { reassign.choose(target) },
			)
		}
	}
}

// Where the material goes and how much: it starts at the most that can move, so usually only the reason is left.
@Composable
private fun QuantityStep(state: OrderReassignmentState, reassign: OrderReassignmentViewModel, saving: () -> Unit) {
	val target = state.target ?: return
	val order = state.order ?: return
	val colors = AppBento.tone(BentoTone.Ink)
	Row(
		Modifier
			.fillMaxWidth()
			.background(colors.container, RoundedCornerShape(AppBento.tile))
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag("reassign-target"),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp),
	) {
		AppText(order.number, StockFlowType.labelMd, colors.content.copy(alpha = 0.8f), Modifier.weight(1f), maxLines = 2)
		Icon(AppIcon.ARROW, StockFlow.colors.primary, size = 22.dp)
		AppText(target.order.number, StockFlowType.titleMd, colors.content, Modifier.weight(1f), maxLines = 2)
	}
	val most = minOf(state.line?.pickedQuantity?.toDoubleOrNull() ?: 0.0, target.line.remaining?.toDoubleOrNull() ?: Double.MAX_VALUE)
	BentoLabel("${text(Text.REASSIGN_QUANTITY)} · ${text(Text.REASSIGN_MAX).replace("{quantity}", formatQuantity(most.toString(), target.line.unit?.decimalPlaces))}")
	TextInput(
		label = text(Text.REASSIGN_QUANTITY),
		value = state.quantity,
		onValueChange = reassign::editQuantity,
		tag = "reassign-quantity",
		numeric = true,
		error = state.quantityError,
	)
	BentoLabel(text(Text.REASSIGN_REASONS))
	val reasons = listOf(Text.REASSIGN_REASON_URGENT, Text.REASSIGN_REASON_PRIORITY, Text.REASSIGN_REASON_SHORTAGE).map { text(it) }
	Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		reasons.chunked(2).forEachIndexed { row, pair ->
			Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
				pair.forEachIndexed { index, reason ->
					BentoChip(reason, selected = state.note == reason, tag = "reassign-reason-${row * 2 + index}") { reassign.editNote(reason) }
				}
			}
		}
	}
	// The keyboard's Done sends the move, as the other forms of the app do.
	TextInput(
		label = text(Text.REASSIGN_NOTE),
		value = state.note,
		onValueChange = reassign::editNote,
		tag = "reassign-note",
		error = state.noteError,
		imeAction = ImeAction.Done,
		onDone = saving,
	)
	PrimaryButton(text(Text.REASSIGN_SAVE), saving, tag = "reassign-save", busy = state.saving, icon = AppIcon.ARROW)
}

// What the API answered about the move: the source order as it stands.
@Composable
private fun ReassignmentResult(state: OrderReassignmentState, reassign: OrderReassignmentViewModel) {
	val moved = state.saved ?: return
	BentoResult(text(Text.REASSIGN_SAVED), "reassign-saved") {
		AppText(moved.number, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
		moved.lines.orEmpty().forEach { line ->
			AppText(
				"${lineTitle(line)} · ${text(Text.REASSIGN_PICKED).replace("{quantity}", formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces))}",
				StockFlowType.bodyMd,
				StockFlow.colors.onSurfaceMuted,
				maxLines = 2,
			)
		}
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
	PrimaryButton(text(Text.REASSIGN_ANOTHER), reassign::another, tag = "reassign-another")
}

// How urgent an alert is, by its tone: what is overdue is the only thing in rose.
private fun alertTone(kind: String): BentoTone =
	when (kind) {
		OrderAlert.BEFORE -> BentoTone.Amber
		OrderAlert.DUE -> BentoTone.Orange
		else -> BentoTone.Rose
	}

// Why an alert was raised, as the person reads it.
private fun alertKind(kind: String): Text =
	when (kind) {
		OrderAlert.BEFORE -> Text.ALERT_BEFORE
		OrderAlert.DUE -> Text.ALERT_DUE
		else -> Text.ALERT_OVERDUE
	}

// The material of the picked line: its code and name.
private fun lineTitle(line: OrderLine): String = listOfNotNull(line.product.code, line.product.name).joinToString(" · ")
