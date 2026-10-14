package com.axzydev.stock.data.locations

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

// One location of a warehouse, as /locations/query gives it (docs/modules/locations.md). The operator places
// counted goods here.
@Serializable
data class Location(
	val id: String,
	val warehouseId: String,
	val warehouseCode: String,
	val code: String,
	val aisle: String? = null,
	val rack: String? = null,
	val level: String? = null,
	val temporary: Boolean = false,
	val active: Boolean = true,
)

// The locations endpoint the app reads. The app never changes a location.
class LocationsApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	// The locations of one warehouse whose code contains `code`, so a scanned code resolves to its location. The
	// filters are the API's own (the code filter matches anywhere in the code), so the caller keeps the exact match.
	suspend fun inWarehouse(token: String, warehouseId: String, code: String): List<Location> =
		http.post("$baseUrl/locations/query") {
			fromApp(language(), token)
			jsonBody()
			setBody(TableQuery(limit = PAGE_LIMIT, filters = mapOf("warehouseId" to warehouseId, "code" to code)))
		}.bodyOrThrow<Page<Location>>().data
}
