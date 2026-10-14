package com.axzydev.stock.feature.receipts

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.receipts.Discrepancy
import com.axzydev.stock.data.receipts.MyReceipt
import com.axzydev.stock.data.receipts.ReceivingApi
import com.axzydev.stock.feature.list.RemoteListViewModel

// The invoices the signed-in operator still has to count or place (docs/modules/material-receiving.md).
class ReceiptsViewModel(
	private val api: ReceivingApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<MyReceipt>(session, unreachable) {
	override suspend fun fetch(token: String): List<MyReceipt> = api.mine(token)
}

// The open discrepancies the reader can resolve, in the warehouses they reach.
class DiscrepanciesViewModel(
	private val api: ReceivingApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<Discrepancy>(session, unreachable) {
	override suspend fun fetch(token: String): List<Discrepancy> = api.discrepancies(token)
}
