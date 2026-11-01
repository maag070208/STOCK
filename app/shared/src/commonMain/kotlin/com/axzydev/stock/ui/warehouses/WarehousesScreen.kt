package com.axzydev.stock.ui.warehouses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.axzydev.stock.feature.warehouses.WarehousesViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.ListSearch
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.StatusTone

// The warehouses the person may see (docs/modules/locations.md). The app only reads them.
@Composable
fun WarehousesScreen(warehouses: WarehousesViewModel, onBack: () -> Unit) {
	val state by warehouses.state.collectAsState()
	LaunchedEffect(warehouses) { warehouses.load() }
	PageScaffold(
		title = text(Text.PAGE_WAREHOUSES),
		subtitle = text(Text.PAGE_WAREHOUSES_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "warehouses-back",
	) {
		var query by rememberSaveable { mutableStateOf("") }
		ListSearch(text(Text.SEARCH_WAREHOUSES), query, { query = it }, "warehouses-search")
		val shown = state.copy(rows = state.rows.filter { query.matches(it.code, it.name) })
		RemoteList(shown, warehouses::load, text(if (state.rows.isEmpty()) Text.EMPTY else Text.NO_RESULTS), "warehouses") { row ->
			DataRow(
				title = row.name,
				tag = "warehouse-${row.id}",
				subtitle = row.code,
				badge = (if (row.active) text(Text.ACTIVE) else text(Text.INACTIVE)) to
					if (row.active) StatusTone.PRODUCTION else StatusTone.PLANNED,
			)
		}
	}
}
