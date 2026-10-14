package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// F2-01 and F2-04 · e2e: warehouses, their temporary location, and the AREA scope over HTTP (deferred from F1-11).
class WarehousesEndpointTest : ApiTest() {

	private fun fields(body: tools.jackson.databind.JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	@Test
	fun `an administrator creates, reads, renames, deactivates and reactivates a warehouse, which is never deleted`() {
		val admin = api.adminToken()
		val code = uniqueCode("W")
		val created = api.post("/warehouses", admin, mapOf("code" to " ${code.lowercase()} ", "name" to "  Almacén central  "))
		assertEquals(201, created.status, created.body)
		assertEquals(code, created.text("code"))
		assertEquals("Almacén central", created.text("name"))
		assertTrue(created.json.get("active").asBoolean())
		assertEquals("TEMPORARY", created.json.get("temporaryLocation").get("code").asString())
		val id = created.text("id")

		assertEquals(code, api.get("/warehouses/$id", admin).text("code"))
		assertEquals("Almacén norte", api.patch("/warehouses/$id", admin, mapOf("name" to "Almacén norte")).text("name"))
		assertEquals("UNKNOWN_FIELD", api.patch("/warehouses/$id", admin, mapOf("code" to "OTHER")).code)
		assertEquals("DUPLICATE_VALUE", api.post("/warehouses", admin, mapOf("code" to code, "name" to "Otro")).code)

		assertFalse(api.delete("/warehouses/$id", admin).json.get("active").asBoolean())
		val kept = api.get("/warehouses/$id", admin)
		assertEquals(200, kept.status)
		assertFalse(kept.json.get("active").asBoolean())
		assertTrue(api.post("/warehouses/$id/reactivate", admin).json.get("active").asBoolean())

		val log = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetType" to "warehouse", "targetId" to id)))
		assertEquals(
			setOf("WAREHOUSE_CREATED", "WAREHOUSE_UPDATED", "WAREHOUSE_DEACTIVATED", "WAREHOUSE_REACTIVATED"),
			log.json.get("data").list.map { it.get("action").asString() }.toSet(),
		)
	}

	@Test
	fun `each field is validated, and every error names its field`() {
		val admin = api.adminToken()
		val empty = api.post("/warehouses", admin, mapOf<String, Any>())
		assertEquals(400, empty.status)
		assertEquals(setOf("code" to "FIELD_REQUIRED", "name" to "FIELD_REQUIRED"), fields(empty.json))
		val wrong = api.post("/warehouses", admin, mapOf("code" to "ALM 1", "name" to "A"))
		assertEquals(setOf("code" to "FORMAT_INVALID", "name" to "FORMAT_INVALID"), fields(wrong.json))
		assertEquals("FIELD_TOO_LONG", api.post("/warehouses", admin, mapOf("code" to "A".repeat(21), "name" to "Almacén")).code)
		assertEquals("UNKNOWN_FIELD", api.post("/warehouses", admin, mapOf("code" to uniqueCode("W"), "name" to "Almacén", "color" to "red")).code)
	}

	@Test
	fun `locations view reads warehouses, and only locations manage changes them`() {
		val office = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/warehouses/query", office.token, mapOf<String, Any>()).code)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		assertEquals(200, api.post("/warehouses/query", manager.token, mapOf<String, Any>()).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/warehouses", manager.token, mapOf("code" to uniqueCode("W"), "name" to "Almacén")).code)
		val id = api.createWarehouse()
		assertEquals("INSUFFICIENT_PERMISSIONS", api.patch("/warehouses/$id", manager.token, mapOf("name" to "Otro")).code)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.delete("/warehouses/$id", manager.token).code)
	}

	@Test
	fun `with AREA a person reads only their warehouses, a list counts only those, and another warehouse is not found`() {
		val prefix = uniqueCode("AR")
		val mine = api.createWarehouse("$prefix-1")
		val other = api.createWarehouse("$prefix-2")
		val manager = api.newUser("WAREHOUSE_MANAGER")
		assertEquals(200, api.put("/users/${manager.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(mine))).status)

		val list = api.post("/warehouses/query", manager.token, mapOf("filters" to mapOf("code" to prefix), "limit" to 50))
		assertEquals(200, list.status, list.body)
		assertEquals(listOf(mine), list.json.get("data").list.map { it.get("id").asString() })
		assertEquals(1, list.json.get("total").asInt())
		assertEquals(200, api.get("/warehouses/$mine", manager.token).status)
		assertEquals("NOT_FOUND", api.get("/warehouses/$other", manager.token).code)

		// With ALL, the administrator sees both.
		assertEquals(2, api.post("/warehouses/query", api.adminToken(), mapOf("filters" to mapOf("code" to prefix))).json.get("total").asInt())
	}

	@Test
	fun `a warehouse given to a person must exist and be active`() {
		val admin = api.adminToken()
		val person = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		val unknown = api.put("/users/${person.id}/warehouses", admin, mapOf("warehouseIds" to listOf(UUID.randomUUID().toString())))
		assertEquals(setOf("warehouseIds" to "CATALOG_VALUE_INVALID"), fields(unknown.json))
		val inactive = api.createWarehouse()
		api.delete("/warehouses/$inactive", admin)
		assertEquals("CATALOG_VALUE_INVALID", api.put("/users/${person.id}/warehouses", admin, mapOf("warehouseIds" to listOf(inactive))).code)
	}

	@Test
	fun `the list filters and sorts by its columns, and refuses an unknown one`() {
		val prefix = uniqueCode("SR")
		api.createWarehouse("$prefix-B")
		api.createWarehouse("$prefix-A")
		val admin = api.adminToken()
		val sorted = api.post("/warehouses/query", admin, mapOf("filters" to mapOf("code" to prefix, "active" to true), "sort" to mapOf("key" to "code", "direction" to "desc")))
		assertEquals(listOf("$prefix-B", "$prefix-A"), sorted.json.get("data").list.map { it.get("code").asString() })
		assertEquals("UNKNOWN_FIELD", api.post("/warehouses/query", admin, mapOf("filters" to mapOf("color" to "red"))).code)
	}
}
