package com.axzydev.stock.data.receipts

import com.axzydev.stock.data.api.PAGE_LIMIT
import com.axzydev.stock.data.api.Page
import com.axzydev.stock.data.api.TableQuery
import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.data.api.jsonBody
import com.axzydev.stock.data.products.DecimalText
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

// The shapes of the receiving endpoints (docs/modules/material-receiving.md). A quantity keeps the digits the API
// sent, as the product lookup does, because the app shows them and never computes with them.

@Serializable
data class Ref(val id: String, val code: String? = null, val name: String? = null)

@Serializable
data class CodeLabel(val code: String, val label: String? = null)

@Serializable
data class UnitRef(val code: String, val label: String? = null, val decimalPlaces: Int? = null)

@Serializable
data class PlacementView(
	val id: String,
	val locationId: String,
	val locationCode: String? = null,
	@Serializable(with = DecimalText::class) val quantity: String,
	val placedAt: String,
)

// What an operator has to do with one line: count it, and place what was counted.
@Serializable
data class AssignmentView(
	val id: String,
	val lineId: String,
	val operator: Ref,
	@Serializable(with = DecimalText::class) val expectedQuantity: String,
	@Serializable(with = DecimalText::class) val countedQuantity: String? = null,
	// counted − expected, once counted.
	@Serializable(with = DecimalText::class) val difference: String? = null,
	// MATCH or DISCREPANCY, once counted.
	val outcome: String? = null,
	val countNotes: String? = null,
	val countedAt: String? = null,
	@Serializable(with = DecimalText::class) val placedQuantity: String,
	val placed: Boolean = false,
	val placements: List<PlacementView> = emptyList(),
)

@Serializable
data class LineView(
	val id: String,
	val product: Ref,
	val unit: UnitRef? = null,
	@Serializable(with = DecimalText::class) val quantity: String,
	val assignments: List<AssignmentView> = emptyList(),
)

@Serializable
data class InvoiceView(
	val id: String,
	val number: String,
	val supplier: Ref,
	val contact: Ref? = null,
	val invoiceDate: String,
	val warehouse: Ref,
	val status: CodeLabel,
	val createdAt: String,
	val lines: List<LineView>? = null,
)

// One invoice the operator has work in, with only their own lines and assignments.
@Serializable
data class MyReceipt(val invoice: InvoiceView, val lines: List<LineView> = emptyList())

@Serializable
data class Discrepancy(
	val assignmentId: String,
	val invoice: Ref,
	val warehouse: Ref,
	val product: Ref,
	val operator: Ref,
	@Serializable(with = DecimalText::class) val expectedQuantity: String,
	@Serializable(with = DecimalText::class) val countedQuantity: String,
	@Serializable(with = DecimalText::class) val difference: String,
	val countNotes: String? = null,
	val countedAt: String? = null,
)

// The receiving endpoints the app uses: what the operator has to receive, and the differences to resolve.
class ReceivingApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun mine(token: String): List<MyReceipt> =
		http.get("$baseUrl/receipts/mine") { fromApp(language(), token) }.bodyOrThrow()

	suspend fun discrepancies(token: String): List<Discrepancy> =
		http.post("$baseUrl/receipts/discrepancies/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(TableQuery(limit = PAGE_LIMIT))
		}.bodyOrThrow<Page<Discrepancy>>().data
}
