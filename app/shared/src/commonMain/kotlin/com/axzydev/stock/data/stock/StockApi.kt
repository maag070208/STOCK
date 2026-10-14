package com.axzydev.stock.data.stock

import com.axzydev.stock.data.api.PAGE_LIMIT
import com.axzydev.stock.data.api.Page
import com.axzydev.stock.data.api.TableQuery
import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.data.api.jsonBody
import com.axzydev.stock.data.products.DecimalText
import com.axzydev.stock.data.receipts.UnitRef
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

// One material's stock in one warehouse, as /stock/products/query gives it (docs/modules/stock.md).
@Serializable
data class ProductTotal(
	val warehouseId: String,
	val warehouseCode: String,
	val productId: String,
	val productCode: String,
	val productName: String,
	val unit: UnitRef? = null,
	@Serializable(with = DecimalText::class) val quantity: String,
	val locations: Long = 0,
)

// The stock endpoints the app reads. Stock changes only through the operations that cause it, so the app never writes.
class StockApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun products(token: String): List<ProductTotal> =
		http.post("$baseUrl/stock/products/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(TableQuery(limit = PAGE_LIMIT))
		}.bodyOrThrow<Page<ProductTotal>>().data
}
