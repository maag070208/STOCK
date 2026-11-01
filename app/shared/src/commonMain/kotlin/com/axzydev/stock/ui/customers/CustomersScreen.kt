package com.axzydev.stock.ui.customers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.axzydev.stock.feature.customers.CustomersViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.ListSearch
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.components.matches
import com.axzydev.stock.ui.components.StatusTone

// The customers of the business (docs/modules/customers.md): the ones the orders of the warehouse are for. The app
// only reads them.
@Composable
fun CustomersScreen(customers: CustomersViewModel, onBack: () -> Unit) {
	val state by customers.state.collectAsState()
	LaunchedEffect(customers) { customers.load() }
	PageScaffold(
		title = text(Text.PAGE_CUSTOMERS),
		subtitle = text(Text.PAGE_CUSTOMERS_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "customers-back",
	) {
		var query by rememberSaveable { mutableStateOf("") }
		ListSearch(text(Text.SEARCH_CUSTOMERS), query, { query = it }, "customers-search")
		val shown = state.copy(rows = state.rows.filter { query.matches(it.code, it.name, it.taxId) })
		RemoteList(shown, customers::load, text(if (state.rows.isEmpty()) Text.EMPTY else Text.NO_RESULTS), "customers") { row ->
			DataRow(
				title = row.name,
				tag = "customer-${row.id}",
				subtitle = listOfNotNull(row.code, row.address?.city).joinToString(" · "),
				badge = (if (row.active) text(Text.ACTIVE) else text(Text.INACTIVE)) to
					if (row.active) StatusTone.PRODUCTION else StatusTone.PLANNED,
			)
		}
	}
}
