package com.axzydev.stock.ui.stock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.axzydev.stock.feature.stock.StockViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.components.ScanSearch
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.format.formatQuantity

// Each material's stock in each warehouse (docs/modules/stock.md). The app only reads it.
@Composable
fun StockScreen(stock: StockViewModel, onBack: () -> Unit) {
	val state by stock.state.collectAsState()
	val query by stock.query.collectAsState()
	LaunchedEffect(stock) { stock.load() }
	PageScaffold(
		title = text(Text.PAGE_STOCK),
		subtitle = text(Text.PAGE_STOCK_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "stock-back",
	) {
		ScanSearch(
			instruction = text(Text.SCAN_MATERIAL),
			searchLabel = text(Text.SEARCH_MATERIAL),
			query = query,
			onQueryChange = stock::edit,
			onScanned = stock::scanned,
			onSubmit = {},
			tag = "stock-search",
		)
		val shown = state.copy(rows = state.rows.filter { query.matches(it.productCode, it.productName, it.warehouseCode) })
		RemoteList(shown, stock::load, text(if (state.rows.isEmpty()) Text.EMPTY else Text.NO_RESULTS), "stock") { row ->
			DataRow(
				title = listOfNotNull(row.productCode, row.productName).joinToString(" · "),
				tag = "stock-${row.warehouseId}-${row.productId}",
				subtitle = listOfNotNull(
					row.warehouseCode,
					row.unit?.label ?: row.unit?.code,
					locationsLabel(row.locations),
				).joinToString(" · "),
				trailing = formatQuantity(row.quantity, row.unit?.decimalPlaces),
				badge = if (row.quantity.trimStart('-', '0', '.').isEmpty()) text(Text.OUT_OF_STOCK) to StatusTone.PLANNED else null,
			)
		}
	}
}

// One location is "1 ubicación"; several are "{count} ubicaciones".
@Composable
private fun locationsLabel(count: Long): String =
	if (count == 1L) text(Text.LOCATION_ONE) else text(Text.LOCATIONS_COUNT).replace("{count}", count.toString())
