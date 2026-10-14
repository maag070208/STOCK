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
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.data.stock.LocationBalance
import com.axzydev.stock.feature.orders.OrderPickState
import com.axzydev.stock.feature.orders.OrderPickViewModel
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

// Picking the material of an order (docs/modules/order-fulfillment.md section 7, barcode-scanning.md section 4). The
// operator opens an order assigned to them, scans the material, sees the balance of each location that holds it, scans
// the code of the one they take it from, and enters the quantity. The API writes the OUT movement and owns the rules.
@Composable
fun MyOrdersScreen(pick: OrderPickViewModel, onBack: () -> Unit) {
	val state by pick.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	LaunchedEffect(pick) { pick.load() }

	val finding = {
		focus.clearFocus()
		keyboard?.hide()
		pick.submitScan()
	}
	val confirming = {
		focus.clearFocus()
		keyboard?.hide()
		pick.submitLocation()
	}

	PageScaffold(
		title = text(Text.PAGE_ORDERS),
		subtitle = text(Text.PAGE_ORDERS_HINT),
		onBack = if (state.order != null) pick::back else onBack,
		backLabel = if (state.order != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "orders-back",
	) {
		when {
			state.order == null -> {
				state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("orders-error")) }
				if (state.loading) AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
				if (state.orders.isEmpty() && !state.loading) AppText(text(Text.EMPTY), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)
				state.orders.forEach { order ->
					PickOrder(order, tag = "order-${order.number}", onClick = { pick.open(order) })
				}
			}

			state.saved != null -> PickResult(state, pick)

			// The order is read again with its lines before anything is scanned.
			state.loading -> AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)

			else -> PickForm(state, pick, finding, confirming)
		}
	}
}

// One order of the list: its number, who it is for, and the day it must leave.
@Composable
private fun PickOrder(order: Order, tag: String, onClick: () -> Unit) {
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
		AppText(order.number, StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 1)
		AppText(order.customer.name ?: order.customer.code.orEmpty(), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
		AppText(
			"${order.warehouse.code.orEmpty()} · ${text(Text.PICK_DUE).replace("{date}", order.dueDate)} · ${order.status.label ?: order.status.code}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 1,
		)
	}
}

// The order being picked: what each line still needs, and the material the operator scans.
@Composable
private fun PickForm(state: OrderPickState, pick: OrderPickViewModel, finding: () -> Unit, confirming: () -> Unit) {
	val order = state.order ?: return
	Card(Modifier.testTag("pick-order")) {
		AppText(order.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			"${order.customer.name ?: order.customer.code.orEmpty()} · ${order.warehouse.code.orEmpty()} · ${order.status.label ?: order.status.code}",
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
		)
		order.lines.orEmpty().forEach { line ->
			AppText(lineText(line), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, Modifier.testTag("pick-line-${line.product.code}"), maxLines = 2)
		}
	}

	// The material, until one of the order's lines is open.
	if (state.line == null) {
		ScannerPanel(onScanned = pick::scanned)
		TextInput(
			label = text(Text.PICK_SCAN),
			value = state.scan,
			onValueChange = pick::editScan,
			tag = "pick-scan",
			icon = AppIcon.SEARCH,
			error = state.fieldError ?: state.scanError,
			imeAction = ImeAction.Search,
			onDone = finding,
		)
		PrimaryButton(text = text(Text.LOOKUP_SEARCH), onClick = finding, tag = "pick-find")
		state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
		return
	}

	val line = state.line
	Card(Modifier.testTag("pick-line")) {
		AppText(lineTitle(line), StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		line.remaining?.let { left ->
			AppText(
				text(Text.PICK_REMAINING).replace("{quantity}", formatQuantity(left, line.unit?.decimalPlaces)),
				StockFlowType.bodyMd,
				StockFlow.colors.onSurfaceMuted,
			)
		}
	}

	// The locations that hold the material: the operator scans the code of one, or taps it here.
	if (state.location == null) {
		AppText(text(Text.PICK_LOCATIONS), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
		state.balances.forEach { balance ->
			PickLocation(balance, tag = "pick-location-${balance.locationCode}", onClick = { pick.choose(balance) })
		}
	}
	ScannerPanel(onScanned = pick::scannedLocation)
	TextInput(
		label = text(Text.PICK_LOCATION_SCAN),
		value = state.locationScan,
		onValueChange = pick::editLocationScan,
		tag = "pick-location-scan",
		icon = AppIcon.SEARCH,
		error = state.locationError,
		imeAction = ImeAction.Search,
		onDone = confirming,
	)
	PrimaryButton(text = text(Text.LOOKUP_SEARCH), onClick = confirming, tag = "pick-location-find")

	// The quantity, once the location is confirmed: never more than the balance it holds.
	state.location?.let { location ->
		AppText(
			text(Text.PICK_MAX).replace("{quantity}", formatQuantity(location.quantity, line.unit?.decimalPlaces)),
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			Modifier.testTag("pick-max"),
		)
		TextInput(
			label = text(Text.PICK_QUANTITY),
			value = state.quantity,
			onValueChange = pick::editQuantity,
			tag = "pick-quantity",
			numeric = true,
			error = state.quantityError,
			imeAction = ImeAction.Done,
			onDone = pick::save,
		)
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
	PrimaryButton(text(Text.PICK_SAVE), pick::save, tag = "pick-save", busy = state.saving)
}

// One location that holds the material: its code, and how much is there.
@Composable
private fun PickLocation(balance: LocationBalance, tag: String, onClick: () -> Unit) {
	val shape = RoundedCornerShape(DesignTokens.Components.cardPanel.rounded.dp)
	Column(
		Modifier
			.fillMaxWidth()
			.background(Color(DesignTokens.Components.cardPanel.backgroundColor), shape)
			.border(1.dp, StockFlow.colors.outline, shape)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.sm.dp)
			.testTag(tag),
	) {
		AppText(balance.locationCode, StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 1)
		AppText(formatQuantity(balance.quantity, null), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
	}
}

// What the API answered about the pick: the order as it stands, and whether it still has material to pick.
@Composable
private fun PickResult(state: OrderPickState, pick: OrderPickViewModel) {
	val picked = state.saved ?: return
	Card(Modifier.testTag("pick-saved")) {
		AppText(text(Text.PICK_SAVED), StockFlowType.bodyLg, StockFlow.colors.success)
		AppText(picked.status.label ?: picked.status.code, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, Modifier.testTag("pick-status"))
		picked.lines.orEmpty().forEach { line ->
			AppText(lineText(line), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
	PrimaryButton(text(Text.PICK_ANOTHER), pick::another, tag = "pick-another")
}

// One line of the order: its material, and what is still to pick out of its quantity.
@Composable
private fun lineText(line: OrderLine): String {
	val title = listOfNotNull(line.product.code, line.product.name).joinToString(" · ")
	val left = line.remaining?.let { formatQuantity(it, line.unit?.decimalPlaces) } ?: line.quantity
	return "$title — ${text(Text.PICK_REMAINING).replace("{quantity}", left)}"
}

// The material of the open line: its code and name.
private fun lineTitle(line: OrderLine): String = listOfNotNull(line.product.code, line.product.name).joinToString(" · ")
