package com.axzydev.stock.data.products

import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ProductUnit(val code: String, val label: String? = null, val decimalPlaces: Int? = null)

// A product as the lookup gives it. `stock` is the total in the person's warehouses, as the API wrote it, or null
// when the person does not read stock.
@Serializable
data class LookupProduct(
	val id: String,
	val code: String,
	val name: String,
	val barcode: String,
	val unit: ProductUnit,
	@Serializable(with = DecimalText::class) val stock: String? = null,
)

// How the value was found: BARCODE, CODE, or NAME (a list the person picks from).
@Serializable
data class LookupResult(val match: String, val products: List<LookupProduct>)

// A quantity keeps the digits the API sent: it is shown, never computed with, so it is not read as a floating point number.
internal object DecimalText : KSerializer<String> {
	override val descriptor = PrimitiveSerialDescriptor("DecimalText", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): String =
		(decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()

	override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

// The products endpoints the app uses (docs/modules/barcode-scanning.md section 3).
class ProductApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	// A scanned or typed value: barcode, then code, then name. No match is BARCODE_NOT_FOUND.
	suspend fun lookup(token: String, value: String): LookupResult =
		http.get("$baseUrl/products/lookup") { fromApp(language(), token); parameter("value", value) }.bodyOrThrow()
}
