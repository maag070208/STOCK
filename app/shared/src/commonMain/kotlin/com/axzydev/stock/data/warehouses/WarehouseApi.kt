package com.axzydev.stock.data.warehouses

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

@Serializable
data class LocationRef(val id: String, val code: String)

// One warehouse, as /warehouses/query gives it (docs/modules/locations.md).
@Serializable
data class Warehouse(
	val id: String,
	val code: String,
	val name: String,
	val active: Boolean = true,
	val temporaryLocation: LocationRef? = null,
	val createdAt: String? = null,
)

// The warehouse endpoints the app reads. The app never changes a warehouse.
class WarehouseApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun query(token: String): List<Warehouse> =
		http.post("$baseUrl/warehouses/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(TableQuery(limit = PAGE_LIMIT))
		}.bodyOrThrow<Page<Warehouse>>().data
}
