package com.axzydev.stock.ui.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.feature.orders.OrderPickState
import com.axzydev.stock.feature.orders.OrderPickViewModel
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
import com.axzydev.stock.ui.components.BentoSteps
import com.axzydev.stock.ui.components.ListSearch
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.ScanSearch
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.riseIn
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Picking the material of an order (docs/modules/order-fulfillment.md section 7, barcode-scanning.md section 4), in three
// steps. The operator scans the material or finds it among what the order still needs, scans the location or finds it
// among the ones that hold it, and confirms a quantity that starts at the most that can be taken. The API writes the OUT
// movement and owns the rules. Back goes one step back.
@Composable
fun MyOrdersScreen(pick: OrderPickViewModel, onBack: () -> Unit) {
	val state by pick.state.collectAsState()
	LaunchedEffect(pick) { pick.load() }

	PageScaffold(
		title = state.order?.number ?: text(Text.PAGE_ORDERS),
		subtitle = state.order?.let { it.customer.name ?: it.customer.code } ?: text(Text.PAGE_ORDERS_HINT),
		onBack = if (state.order != null) pick::back else onBack,
		backLabel = if (state.order != null) text(Text.BACK) else text(Text.BACK_MODULES),
		backTag = "orders-back",
	) {
		when {
			state.order == null -> OrderList(state, pick)

			state.saved != null -> PickResult(state, pick)

			// The order is read again with its lines before anything is scanned.
			state.loading -> AppText(text(Text.LOADING), StockFlowType.bodyLg, StockFlow.colors.onSurfaceMuted)

			else -> PickForm(state, pick)
		}
	}
}

// The orders of the operator, with the day each must leave.
@Composable
private fun OrderList(state: OrderPickState, pick: OrderPickViewModel) {
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("orders-error")) }
	if (state.loading) return
	if (state.orders.isEmpty()) {
		BentoState(text(Text.EMPTY_ORDERS), "orders-empty", text(Text.EMPTY_HINT))
		return
	}
	ListSearch(text(Text.SEARCH_ORDERS), state.listQuery, pick::editListQuery, "orders-search")
	val shown = state.orders.filter { state.listQuery.matches(it.number, it.customer.name, it.customer.code) }
	if (shown.isEmpty()) BentoState(text(Text.NO_RESULTS), "orders-no-results", icon = AppIcon.SEARCH)
	shown.forEachIndexed { index, order ->
		Column(Modifier.riseIn(index)) {
			BentoChoiceRow(
				title = order.number,
				subtitle = "${order.customer.name ?: order.customer.code.orEmpty()} · ${order.warehouse.code.orEmpty()}",
				detail = order.dueDate,
				tag = "order-${order.number}",
				onClick = { pick.open(order) },
				icon = AppIcon.CLIPBOARD,
			)
		}
	}
}

// The three steps, each with what the person needs and nothing else.
@Composable
private fun PickForm(state: OrderPickState, pick: OrderPickViewModel) {
	val line = state.line
	val location = state.location
	BentoSteps(
		listOf(text(Text.REASSIGN_STEP_MATERIAL), text(Text.STEP_LOCATION), text(Text.REASSIGN_STEP_QUANTITY)),
		current = if (line == null) 0 else if (location == null) 1 else 2,
		modifier = Modifier.testTag("pick-order"),
	)
	when {
		line == null -> MaterialStep(state, pick)
		location == null -> LocationStep(state, line, pick)
		else -> {
			BentoHero(lineTitle(line), location.locationCode, text(Text.STEP_LOCATION), "pick-line", subtitle = remaining(line))
			AppText(
				text(Text.PICK_MAX).replace("{quantity}", formatQuantity(location.quantity, line.unit?.decimalPlaces)),
				StockFlowType.bodyMd,
				StockFlow.colors.onSurfaceMuted,
				Modifier.testTag("pick-max"),
			)
			BentoQuantity(text(Text.PICK_QUANTITY), state.quantity, pick::editQuantity, "pick-quantity", state.quantityError, onDone = pick::save)
			state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
			PrimaryButton(text(Text.PICK_SAVE), pick::save, tag = "pick-save", busy = state.saving, icon = AppIcon.CHECK)
		}
	}
}

// What the order still needs, narrowed by what is written.
@Composable
private fun MaterialStep(state: OrderPickState, pick: OrderPickViewModel) {
	val pending = state.order?.lines.orEmpty().filter { line -> line.remaining?.let { Decimals.above(it, "0") } ?: true }
	val shown = pending.filter { state.scan.matches(it.product.code, it.product.name) }
	ScanSearch(
		instruction = text(Text.SCAN_MATERIAL),
		searchLabel = text(Text.PICK_SCAN),
		query = state.scan,
		onQueryChange = pick::editScan,
		onScanned = pick::scanned,
		onSubmit = pick::submitScan,
		tag = "pick-scan",
		error = state.fieldError ?: state.scanError,
	) {
		state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
		if (shown.isNotEmpty()) BentoLabel("${text(Text.PICK_PENDING)} · ${shown.size}")
		shown.forEachIndexed { index, line ->
			Column(Modifier.riseIn(index)) {
				BentoChoiceRow(
					title = lineTitle(line),
					detail = remaining(line),
					tag = "pick-line-${line.product.code}",
					onClick = { pick.choose(line) },
				)
			}
		}
	}
}

// The locations that hold the material, narrowed by what is written: a scan or a touch confirms one.
@Composable
private fun LocationStep(state: OrderPickState, line: OrderLine, pick: OrderPickViewModel) {
	val shown = state.balances.filter { state.locationScan.matches(it.locationCode) }
	BentoHero(lineTitle(line), remaining(line), text(Text.REASSIGN_STEP_QUANTITY), "pick-line")
	ScanSearch(
		instruction = text(Text.SCAN_LOCATION),
		searchLabel = text(Text.PICK_LOCATION_SCAN),
		query = state.locationScan,
		onQueryChange = pick::editLocationScan,
		onScanned = pick::scannedLocation,
		onSubmit = pick::submitLocation,
		tag = "pick-location-scan",
		error = state.locationError,
	) {
		state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
		BentoLabel(text(Text.PICK_LOCATIONS))
		shown.forEach { balance ->
			BentoChoiceRow(
				title = balance.locationCode,
				detail = formatQuantity(balance.quantity, line.unit?.decimalPlaces),
				tag = "pick-location-${balance.locationCode}",
				onClick = { pick.choose(balance) },
				icon = AppIcon.WAREHOUSE,
			)
		}
	}
}

// What the API answered about the pick: the order as it stands, and whether it still has material to pick.
@Composable
private fun PickResult(state: OrderPickState, pick: OrderPickViewModel) {
	val picked = state.saved ?: return
	BentoResult(text(Text.PICK_SAVED), "pick-saved") {
		AppText(picked.status.label ?: picked.status.code, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, Modifier.testTag("pick-status"))
		picked.lines.orEmpty().filter { line -> line.remaining?.let { Decimals.above(it, "0") } ?: false }.forEach { line ->
			AppText("${lineTitle(line)} · ${remaining(line)}", StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
	}
	state.error?.let { AppText(it, StockFlowType.bodyLg, StockFlow.colors.danger, Modifier.testTag("pick-error")) }
	PrimaryButton(text(Text.PICK_ANOTHER), pick::another, tag = "pick-another", icon = AppIcon.SCAN)
}

// What a line still needs.
@Composable
private fun remaining(line: OrderLine): String =
	text(Text.PICK_REMAINING).replace("{quantity}", line.remaining?.let { formatQuantity(it, line.unit?.decimalPlaces) } ?: line.quantity)

// The material of a line: its code and name.
private fun lineTitle(line: OrderLine): String = listOfNotNull(line.product.code, line.product.name).joinToString(" · ")
