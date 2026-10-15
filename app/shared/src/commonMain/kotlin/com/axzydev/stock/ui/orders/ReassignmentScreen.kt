package com.axzydev.stock.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.axzydev.stock.data.orders.Order
import com.axzydev.stock.data.orders.OrderAlert
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.feature.orders.OrderReassignmentState
import com.axzydev.stock.feature.orders.OrderReassignmentViewModel
import com.axzydev.stock.feature.orders.ReassignmentTarget
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

// Reassigning picked material (docs/modules/order-fulfillment.md section 8, barcode-scanning.md section 4). The person
// opens the alert of an order that is falling due, scans the material that was already picked, chooses another open
// order of the same warehouse that needs it, and moves a quantity with a note. No stock changes.
@Composable
fun ReassignmentScreen(reassign: OrderReassignmentViewModel, onBack: () -> Unit) {
	val state by reassign.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	LaunchedEffect(reassign) { reassign.load() }

	val finding = {
		focus.clearFocus()
		keyboard?.hide()
		reassign.submitScan()
	}
	val saving = {
		focus.clearFocus()
		keyboard?.hide()
		reassign.save()
	}

	PageScaffold(
		title = text(Text.PAGE_REASSIGNMENT),
		subtitle = text(Text.PAGE_REASSIGNMENT_HINT),
		onBack = if (state.order != null) reassign::back else onBack,
		backLabel = if (state.order != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "reassign-back",
	) {
		when {
			state.order == null -> AlertList(state, reassign)

			state.saved != null -> ReassignmentResult(state, reassign)

			// The order is read again with its lines before anything is scanned.
			state.loading -> AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)

			else -> ReassignmentForm(state, reassign, finding, saving)
		}
	}
}

// The alerts of the signed-in person: the orders that are due tomorrow, today, or past their date.
@Composable
private fun AlertList(state: OrderReassignmentState, reassign: OrderReassignmentViewModel) {
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
	if (state.loading) AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
	if (state.alerts.isEmpty() && !state.loading) AppText(text(Text.EMPTY), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
	if (state.alerts.isNotEmpty()) AppText(text(Text.REASSIGN_ALERTS), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
	state.alerts.forEach { alert ->
		AlertRow(alert, tag = "alert-${alert.order.number}", onClick = { reassign.open(alert) })
	}
}

// One alert: the order it is about, why it was raised, and the day it must leave.
@Composable
private fun AlertRow(alert: OrderAlert, tag: String, onClick: () -> Unit) {
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
		AppText(alert.order.number, StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 1)
		// The kind of the alert, in the language of the person reading it.
		AppText(
			"${text(alertKind(alert.kind))} · ${text(Text.PICK_DUE).replace("{date}", alert.order.dueDate)}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 1,
		)
		AppText(
			"${alert.order.customer.name ?: alert.order.customer.code.orEmpty()} · ${alert.order.warehouse.code.orEmpty()}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 1,
		)
	}
}

// The order being reassigned: the alert's order, and the material that was already picked.
@Composable
private fun ReassignmentForm(
	state: OrderReassignmentState,
	reassign: OrderReassignmentViewModel,
	finding: () -> Unit,
	saving: () -> Unit,
) {
	val order = state.order ?: return
	Card(Modifier.testTag("reassign-order")) {
		AppText(order.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			"${order.customer.name ?: order.customer.code.orEmpty()} · ${order.warehouse.code.orEmpty()} · ${order.status.label ?: order.status.code}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
		)
	}

	// The material, until one of the order's picked lines is open.
	if (state.line == null) {
		ScannerPanel(onScanned = reassign::scanned)
		TextInput(
			label = text(Text.REASSIGN_SCAN),
			value = state.scan,
			onValueChange = reassign::editScan,
			tag = "reassign-scan",
			icon = AppIcon.SEARCH,
			error = state.fieldError ?: state.scanError,
			imeAction = ImeAction.Search,
			onDone = finding,
		)
		PrimaryButton(text = text(Text.LOOKUP_SEARCH), onClick = finding, tag = "reassign-find")
		state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
		return
	}

	val line = state.line
	Card(Modifier.testTag("reassign-line")) {
		AppText(lineTitle(line), StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		AppText(
			text(Text.REASSIGN_PICKED).replace("{quantity}", formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces)),
			StockFlowType.bodyLg,
			StockFlow.colors.onSurface,
			Modifier.testTag("reassign-picked"),
		)
	}

	// The open orders of the same warehouse that need the material: the person picks one.
	if (state.target == null) {
		AppText(text(Text.REASSIGN_TARGETS), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
		if (state.targets.isEmpty()) AppText(text(Text.EMPTY), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
		state.targetError?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-target-error")) }
		state.targets.forEach { target ->
			TargetRow(target, tag = "reassign-target-${target.order.number}", onClick = { reassign.choose(target) })
		}
	} else {
		val target = state.target
		Card(Modifier.testTag("reassign-target")) {
			AppText(text(Text.REASSIGN_TARGETS), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
			AppText(target.order.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
			target.line.remaining?.let { needs ->
				AppText(
					text(Text.PICK_REMAINING).replace("{quantity}", formatQuantity(needs, target.line.unit?.decimalPlaces)),
					StockFlowType.bodyMd,
					StockFlow.colors.onSurfaceMuted,
				)
			}
		}
		TextInput(
			label = text(Text.REASSIGN_NOTE),
			value = state.note,
			onValueChange = reassign::editNote,
			tag = "reassign-note",
			error = state.noteError,
		)
		// The keyboard's Done sends the move, as the other forms of the app do.
		TextInput(
			label = text(Text.REASSIGN_QUANTITY),
			value = state.quantity,
			onValueChange = reassign::editQuantity,
			tag = "reassign-quantity",
			numeric = true,
			error = state.quantityError,
			imeAction = ImeAction.Done,
			onDone = saving,
		)
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
	if (state.target != null) PrimaryButton(text(Text.REASSIGN_SAVE), saving, tag = "reassign-save", busy = state.saving)
}

// One order that needs the material: its number, who it is for, and what it still needs.
@Composable
private fun TargetRow(target: ReassignmentTarget, tag: String, onClick: () -> Unit) {
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
		AppText(target.order.number, StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			target.order.customer.name ?: target.order.customer.code.orEmpty(),
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 1,
		)
		target.line.remaining?.let { needs ->
			AppText(
				text(Text.PICK_REMAINING).replace("{quantity}", formatQuantity(needs, target.line.unit?.decimalPlaces)),
				StockFlowType.bodyMd,
				StockFlow.colors.onSurfaceMuted,
				maxLines = 1,
			)
		}
	}
}

// What the API answered about the move: the source order as it stands.
@Composable
private fun ReassignmentResult(state: OrderReassignmentState, reassign: OrderReassignmentViewModel) {
	val moved = state.saved ?: return
	Card(Modifier.testTag("reassign-saved")) {
		AppText(text(Text.REASSIGN_SAVED), StockFlowType.bodyLg, StockFlow.colors.success)
		AppText(moved.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		moved.lines.orEmpty().forEach { line ->
			AppText(
				text(Text.REASSIGN_PICKED).replace("{quantity}", formatQuantity(line.pickedQuantity, line.unit?.decimalPlaces)),
				StockFlowType.bodyMd,
				StockFlow.colors.onSurfaceMuted,
				maxLines = 2,
			)
		}
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("reassign-error")) }
	PrimaryButton(text(Text.REASSIGN_ANOTHER), reassign::another, tag = "reassign-another")
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
