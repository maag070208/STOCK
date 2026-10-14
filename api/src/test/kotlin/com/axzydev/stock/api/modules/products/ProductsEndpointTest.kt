package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

// F2-06 · e2e: materials. The barcode is the code by default and unique among active products; the unit is a catalog value.
class ProductsEndpointTest : ApiTest() {

	private fun fields(body: JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	private fun code() = uniqueCode("P").replace("-", "")

	private fun create(body: Map<String, Any?>) = api.post("/products", api.adminToken(), body)

	@Test
	fun `an administrator creates a product, its barcode is its code, and a name with inches is kept as written`() {
		val code = code()
		val created = create(mapOf("code" to code.lowercase(), "name" to "  VARILLA   SINFIN GALV. 5/16\" X 1 MT ", "unit" to "PIECE"))
		assertEquals(201, created.status, created.body)
		assertEquals(code, created.text("code"))
		assertEquals("VARILLA SINFIN GALV. 5/16\" X 1 MT", created.text("name"))
		assertEquals(code, created.text("barcode"))
		assertEquals("PIECE", created.json.get("unit").get("code").asString())
		assertEquals("Pieza", created.json.get("unit").get("label").asString())
		assertTrue(created.json.get("active").asBoolean())
		val id = created.text("id")
		assertEquals(code, api.get("/products/$id", api.adminToken()).text("code"))
	}

	@Test
	fun `a barcode or a code that exists is refused, and each error names its field`() {
		val first = code()
		create(mapOf("code" to first, "name" to "Primero", "unit" to "PIECE"))
		val sameBarcode = create(mapOf("code" to code(), "name" to "Segundo", "unit" to "PIECE", "barcode" to first))
		assertEquals(409, sameBarcode.status)
		assertEquals(setOf("barcode" to "DUPLICATE_VALUE"), fields(sameBarcode.json))
		assertEquals(setOf("code" to "DUPLICATE_VALUE"), fields(create(mapOf("code" to first, "name" to "Tercero", "unit" to "PIECE", "barcode" to code())).json))
	}

	@Test
	fun `every field is validated`() {
		val empty = create(mapOf())
		assertEquals(setOf("code" to "FIELD_REQUIRED", "name" to "FIELD_REQUIRED", "unit" to "FIELD_REQUIRED"), fields(empty.json))
		val wrong = create(mapOf("code" to "A B", "name" to "TUBO @4", "unit" to "LITER", "barcode" to "7501031311308"))
		assertEquals(
			setOf("code" to "FORMAT_INVALID", "name" to "FORMAT_INVALID", "unit" to "CATALOG_VALUE_INVALID", "barcode" to "BARCODE_INVALID"),
			fields(wrong.json),
		)
		assertEquals("UNKNOWN_FIELD", create(mapOf("code" to code(), "name" to "Tubo", "unit" to "PIECE", "stock" to 5)).code)
	}

	@Test
	fun `a product changes its name, unit and barcode, never its code, and a deactivated one stays in history`() {
		val admin = api.adminToken()
		val id = create(mapOf("code" to code(), "name" to "Tubo", "unit" to "PIECE")).text("id")
		val barcode = "7501031311309"
		val changed = api.patch("/products/$id", admin, mapOf("name" to "Tubo  galvanizado", "barcode" to barcode))
		assertEquals(200, changed.status, changed.body)
		assertEquals("Tubo galvanizado", changed.text("name"))
		assertEquals(barcode, changed.text("barcode"))
		assertEquals("UNKNOWN_FIELD", api.patch("/products/$id", admin, mapOf("code" to "OTHER")).code)
		assertEquals("FIELD_REQUIRED", api.patch("/products/$id", admin, mapOf("name" to " ")).code)

		assertFalse(api.delete("/products/$id", admin).json.get("active").asBoolean())
		assertEquals(200, api.get("/products/$id", admin).status)
		// The barcode is unique among active products only: a new product can take it while this one is inactive.
		assertEquals(201, create(mapOf("code" to code(), "name" to "Otro tubo", "unit" to "PIECE", "barcode" to barcode)).status)
		assertEquals(setOf("barcode" to "DUPLICATE_VALUE"), fields(api.post("/products/$id/reactivate", admin).json))

		val log = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetType" to "product", "targetId" to id)))
		assertEquals(setOf("PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DEACTIVATED"), log.json.get("data").list.map { it.get("action").asString() }.toSet())
	}

	@Test
	fun `everyone who works with materials reads them, and only products manage changes them`() {
		val id = create(mapOf("code" to code(), "name" to "Codo", "unit" to "PIECE")).text("id")
		val office = api.newUser("OFFICE")
		assertEquals(200, api.get("/products/$id", office.token).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/products", office.token, mapOf("code" to code(), "name" to "Codo", "unit" to "PIECE")).code)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.patch("/products/$id", office.token, mapOf("name" to "Otro")).code)
		val operator = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		assertEquals(200, api.call("GET", "/products/$id", operator.token, channel = "app").status)
		val appUser = api.newUser("APP_USER", channel = "app")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.call("GET", "/products/$id", appUser.token, channel = "app").code)
	}

	@Test
	fun `the list filters and sorts by its columns`() {
		val prefix = code()
		create(mapOf("code" to "${prefix}B", "name" to "Bravo", "unit" to "PIECE"))
		create(mapOf("code" to "${prefix}A", "name" to "Alfa", "unit" to "PIECE"))
		val page = api.post("/products/query", api.adminToken(), mapOf("filters" to mapOf("code" to prefix, "unit" to "PIECE", "active" to true), "sort" to mapOf("key" to "name", "direction" to "asc")))
		assertEquals(200, page.status, page.body)
		assertEquals(listOf("Alfa", "Bravo"), page.json.get("data").list.map { it.get("name").asString() })
		assertEquals(2, page.json.get("total").asInt())
	}
}
