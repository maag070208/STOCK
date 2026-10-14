package com.axzydev.stock.data.orders

import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.api.PAGE_LIMIT
import com.axzydev.stock.data.api.Page
import com.axzydev.stock.data.api.TableSort
import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.data.api.jsonBody
import com.axzydev.stock.data.products.DecimalText
import com.axzydev.stock.data.receipts.CodeLabel
import com.axzydev.stock.data.receipts.Ref
import com.axzydev.stock.data.receipts.UnitRef
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

// The shapes of the order endpoints (docs/modules/order-fulfillment.md). A quantity keeps the digits the API sent, as
// the product lookup does, because the app shows them and never computes with them.

@Serializable
data class OrderLine(
	val id: String,
	val product: Ref,
	val unit: UnitRef? = null,
	@Serializable(with = DecimalText::class) val quantity: String,
	@Serializable(with = DecimalText::class) val pickedQuantity: String,
) {
	// What the line still needs: its quantity minus what is already picked.
	val remaining: String? get() = Decimals.subtract(quantity, pickedQuantity)
}

@Serializable
data class Order(
	val id: String,
	val number: String,
	val warehouse: Ref,
	val customer: Ref,
	val contact: Ref? = null,
	val dueDate: String,
	val status: CodeLabel,
	val operator: Ref? = null,
	val lines: List<OrderLine>? = null,
) {
	// An order still being picked: the only ones a pick works on (order-fulfillment.md section 6).
	val open: Boolean get() = status.code == CREATED || status.code == FULFILLING

	companion object {
		const val CREATED = "CREATED"
		const val FULFILLING = "FULFILLING"
		const val FULFILLED = "FULFILLED"
	}
}

// The filters of the orders table: the assigned operator, and one or more status codes (the API takes a list).
@Serializable
private data class OrderFilters(val operatorId: String, val status: List<String>)

@Serializable
private data class OrdersQuery(val filters: OrderFilters, val limit: Int = PAGE_LIMIT, val sort: TableSort? = null)

// The pick of one line, out of one location. The quantity travels as text, so no digit is lost on the way.
@Serializable
private data class PickBody(val lineId: String, val locationId: String, val quantity: String)

// The order endpoints the app uses: the open orders assigned to the operator, and the pick of a line.
class OrdersApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	// The orders assigned to this operator that are still to pick, the most urgent due date first.
	suspend fun open(token: String, operatorId: String): List<Order> =
		http.post("$baseUrl/orders/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(OrdersQuery(OrderFilters(operatorId, OPEN), sort = TableSort("dueDate", "asc")))
		}.bodyOrThrow<Page<Order>>().data

	// One pick: how much of a line left a location. The API answers the order as it stands after the pick.
	suspend fun pick(token: String, orderId: String, lineId: String, locationId: String, quantity: String): Order =
		http.post("$baseUrl/orders/$orderId/picks") {
			fromApp(language(), token)
			jsonBody()
			setBody(PickBody(lineId, locationId, quantity))
		}.bodyOrThrow()

	suspend fun order(token: String, orderId: String): Order =
		http.get("$baseUrl/orders/$orderId") { fromApp(language(), token) }.bodyOrThrow()

	private companion object {
		// The statuses a pick works on (order-fulfillment.md section 6).
		val OPEN = listOf(Order.CREATED, Order.FULFILLING)
	}
}
