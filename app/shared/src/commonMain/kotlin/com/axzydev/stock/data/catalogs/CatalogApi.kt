package com.axzydev.stock.data.catalogs

import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// One item of a catalog: its code, its label in the language of the request, and what its attributes say about it
// (docs/modules/catalogs, i18n/README.md). The attributes are read as they come, so a new one does not need a change
// here.
@Serializable
data class CatalogItem(
	val code: String,
	val label: String? = null,
	val description: String? = null,
	val active: Boolean = true,
	val attributes: Map<String, JsonElement> = emptyMap(),
) {
	// An attribute that reads as a flag, like `terminal` on an order status.
	fun isFlag(attribute: String): Boolean = (attributes[attribute] as? JsonPrimitive)?.booleanOrNull == true
}

// The catalogs endpoint the app uses. The catalog is data: the codes come from the API, and the labels come in the
// language of the request.
class CatalogApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun items(token: String, code: String): List<CatalogItem> =
		http.get("$baseUrl/catalogs/$code/items") { fromApp(language(), token) }.bodyOrThrow()
}
