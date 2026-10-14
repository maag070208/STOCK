package com.axzydev.stock.ui.warehouses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.axzydev.stock.feature.warehouses.WarehousesViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.RemoteList
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
		RemoteList(state, warehouses::load, text(Text.EMPTY), "warehouses") { row ->
			DataRow(
				title = listOf(row.code, row.name).joinToString(" · "),
				tag = "warehouse-${row.id}",
				trailing = row.temporaryLocation?.code,
				badge = (if (row.active) text(Text.ACTIVE) else text(Text.INACTIVE)) to
					if (row.active) StatusTone.PRODUCTION else StatusTone.PLANNED,
			)
		}
	}
}
