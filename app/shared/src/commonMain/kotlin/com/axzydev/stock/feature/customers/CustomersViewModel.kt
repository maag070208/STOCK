package com.axzydev.stock.feature.customers

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.customers.Customer
import com.axzydev.stock.data.customers.CustomersApi
import com.axzydev.stock.feature.list.RemoteListViewModel

// The customers of the business (docs/modules/customers.md), the ones the orders of the warehouse are for.
class CustomersViewModel(
	private val api: CustomersApi,
	session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<Customer>(session, unreachable) {
	override suspend fun fetch(token: String): List<Customer> = api.query(token)
}
