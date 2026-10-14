package com.axzydev.stock.feature.warehouses

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.warehouses.Warehouse
import com.axzydev.stock.data.warehouses.WarehouseApi
import com.axzydev.stock.feature.list.RemoteListViewModel

// The warehouses the person may see (docs/modules/locations.md). The app only reads them.
class WarehousesViewModel(
	private val api: WarehouseApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<Warehouse>(session, unreachable) {
	override suspend fun fetch(token: String): List<Warehouse> = api.query(token)
}
