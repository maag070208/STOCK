package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.util.UUID

// F3-09 · e2e: the lookup of a scanned value (barcode-scanning.md section 3): barcode, then code, then name.
// Several matches by name are a list, and nothing is chosen. The lookup creates nothing.
class LookupEndpointTest : ApiTest() {

	private fun create(name: String, barcode: String? = null) =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("L").replace("-", ""), "name" to name, "unit" to "PIECE", "barcode" to barcode)).json

	private fun lookup(value: String, token: String = api.adminToken(), channel: String = "web") =
		api.call("GET", "/products/lookup?value=" + URLEncoder.encode(value, "UTF-8"), token, channel = channel)

	@Test
	fun `a barcode finds its product, and a printed code finds it too`() {
		// A barcode of its own: products of other tests stay active, and the barcode is unique among active products.
		val barcode = "BC" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
		val withBarcode = create("Codo galvanizado", barcode = barcode)
		val found = lookup(barcode).json
		assertEquals("BARCODE", found.get("match").asString())
		assertEquals(listOf(withBarcode.get("id").asString()), found.get("products").list.map { it.get("id").asString() })
		val byCode = lookup(withBarcode.get("code").asString().lowercase()).json
		assertEquals("CODE", byCode.get("match").asString())
		assertEquals("Pieza", byCode.get("products").get(0).get("unit").get("label").asString())
	}

	@Test
	fun `a name with three matches gives the three, and nothing is chosen`() {
		val word = "Abrazadera" + uniqueCode("N").takeLast(6).lowercase()
		repeat(3) { create("$word ${it + 1}\"") }
		val found = lookup(word).json
		assertEquals("NAME", found.get("match").asString())
		assertEquals(3, found.get("products").size())
	}

	@Test
	fun `a value that matches nothing is BARCODE_NOT_FOUND, and an operator on the app can look up`() {
		assertEquals("BARCODE_NOT_FOUND", lookup("NADA" + uniqueCode("X").takeLast(8)).code)
		val operator = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		val product = create("Arandela plana")
		assertEquals(200, lookup(product.get("code").asString(), operator.token, "app").status)
		assertEquals("INSUFFICIENT_PERMISSIONS", lookup("X", api.newUser("APP_USER", channel = "app").token, "app").code)
	}
}
