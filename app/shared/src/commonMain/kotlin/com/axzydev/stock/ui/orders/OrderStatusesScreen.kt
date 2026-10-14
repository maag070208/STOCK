package com.axzydev.stock.ui.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.axzydev.stock.data.catalogs.CatalogItem
import com.axzydev.stock.feature.orders.OrderStatusesViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.components.StatusTone

// The states an order goes through, from the API's own catalog (docs/modules/order-fulfillment.md): created,
// fulfilling, fulfilled, validated or cancelled. A terminal state is where the order stops.
@Composable
fun OrderStatusesScreen(statuses: OrderStatusesViewModel, onBack: () -> Unit) {
	val state by statuses.state.collectAsState()
	LaunchedEffect(statuses) { statuses.load() }
	PageScaffold(
		title = text(Text.PAGE_ORDER_STATUS),
		subtitle = text(Text.PAGE_ORDER_STATUS_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "order-status-back",
	) {
		RemoteList(state, statuses::load, text(Text.EMPTY), "order-status") { row ->
			DataRow(
				title = row.label ?: row.code,
				tag = "order-status-${row.code}",
				subtitle = row.description,
				trailing = row.code,
				badge = if (row.isTerminal()) text(Text.ORDER_STATUS_TERMINAL) to StatusTone.DEVELOPMENT else null,
			)
		}
	}
}

private fun CatalogItem.isTerminal(): Boolean = isFlag("terminal")
