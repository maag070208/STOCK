package com.axzydev.stock.feature.orders

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.catalogs.CatalogApi
import com.axzydev.stock.data.catalogs.CatalogItem
import com.axzydev.stock.feature.list.RemoteListViewModel

// The states an order goes through (docs/modules/order-fulfillment.md, catalog ORDER_STATUS). The orders themselves
// arrive with F5; the states the API already holds are read today.
class OrderStatusesViewModel(
	private val api: CatalogApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<CatalogItem>(session, unreachable) {
	override suspend fun fetch(token: String): List<CatalogItem> = api.items(token, ORDER_STATUS)

	companion object {
		const val ORDER_STATUS = "ORDER_STATUS"
	}
}
