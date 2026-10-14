package com.axzydev.stock.data.customers

import com.axzydev.stock.data.api.PAGE_LIMIT
import com.axzydev.stock.data.api.Page
import com.axzydev.stock.data.api.TableQuery
import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.data.api.jsonBody
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

// The city of a customer's address, which the list shows under the name. The rest of the address is not read.
@Serializable
data class CustomerAddress(val city: String? = null)

// A customer, as /customers/query gives it (docs/modules/customers.md). Only active customers can be chosen for a new
// order; a deactivated one stays on the orders that name it.
@Serializable
data class Customer(
	val id: String,
	val code: String,
	val name: String,
	val taxId: String,
	val address: CustomerAddress? = null,
	val active: Boolean = true,
)

// The customers endpoints the app uses. Customers are master data, and the app only reads them.
class CustomersApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun query(token: String): List<Customer> =
		http.post("$baseUrl/customers/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(TableQuery(limit = PAGE_LIMIT))
		}.bodyOrThrow<Page<Customer>>().data
}
