package com.axzydev.stock.feature.stock

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.stock.ProductTotal
import com.axzydev.stock.data.stock.StockApi
import com.axzydev.stock.feature.list.RemoteListViewModel

// Each material's stock in each warehouse (docs/modules/stock.md). The app only reads it.
class StockViewModel(
	private val api: StockApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<ProductTotal>(session, unreachable) {
	override suspend fun fetch(token: String): List<ProductTotal> = api.products(token)
}
