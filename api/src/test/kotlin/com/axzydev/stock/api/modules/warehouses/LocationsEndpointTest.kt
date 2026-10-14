package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.util.UUID

// F2-02, F2-03, F2-04 and F2-05 · e2e: the rack form, all or nothing; the temporary location; groups by aisle and rack.
class LocationsEndpointTest : ApiTest() {

	private fun fields(body: JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int) =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))

	private fun codes(filters: Map<String, Any>, token: String = api.adminToken(), channel: String = "web"): List<String> {
		val page = api.post("/locations/query", token, mapOf("filters" to filters, "limit" to 200), channel)
		assertEquals(200, page.status, page.body)
		return page.json.get("data").list.map { it.get("code").asString() }
	}

	@Test
	fun `aisle K, rack 05 and three levels create K05A, K05B and K05C, and the same rack again creates nothing`() {
		val warehouse = api.createWarehouse()
		val created = rack(warehouse, "k", "05", 3)
		assertEquals(201, created.status, created.body)
		assertEquals(listOf("K05A", "K05B", "K05C"), created.json.get("locations").list.map { it.get("code").asString() })

		val again = rack(warehouse, "K", "05", 5)
		assertEquals(409, again.status)
		assertEquals("DUPLICATE_VALUE", again.code)
		assertEquals(setOf("codes.K05A", "codes.K05B", "codes.K05C"), fields(again.json).map { it.first }.toSet())
		// All or nothing: D and E were not created either.
		assertEquals(listOf("K05A", "K05B", "K05C"), codes(mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05")))

		// The same codes in another warehouse are another set of locations.
		assertEquals(201, rack(api.createWarehouse(), "K", "05", 3).status)
	}

	@Test
	fun `each part of the rack is validated, and every error names its field`() {
		val warehouse = api.createWarehouse()
		val wrong = rack(warehouse, "KK", "5", 27)
		assertEquals(400, wrong.status)
		assertEquals(setOf("aisle" to "FORMAT_INVALID", "rack" to "FORMAT_INVALID", "levels" to "FORMAT_INVALID"), fields(wrong.json))
		val empty = api.post("/locations/racks", api.adminToken(), mapOf<String, Any>())
		assertEquals(
			setOf("warehouseId" to "FIELD_REQUIRED", "aisle" to "FIELD_REQUIRED", "rack" to "FIELD_REQUIRED", "levels" to "FIELD_REQUIRED"),
			fields(empty.json),
		)
		assertEquals(setOf("warehouseId" to "CATALOG_VALUE_INVALID"), fields(rack(UUID.randomUUID().toString(), "K", "05", 1).json))
		api.delete("/warehouses/$warehouse", api.adminToken())
		assertEquals("CATALOG_VALUE_INVALID", rack(warehouse, "K", "05", 1).code)
	}

	@Test
	fun `a rack group holds its levels only, an aisle group every rack of the aisle, and the groups are listed`() {
		val warehouse = api.createWarehouse()
		rack(warehouse, "K", "05", 3)
		rack(warehouse, "K", "06", 2)
		rack(warehouse, "L", "05", 1)
		assertEquals(listOf("K05A", "K05B", "K05C"), codes(mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05")))
		assertEquals(listOf("K05A", "K05B", "K05C", "K06A", "K06B"), codes(mapOf("warehouseId" to warehouse, "aisle" to "K")))

		val groups = api.get("/warehouses/$warehouse/location-groups", api.adminToken())
		assertEquals(200, groups.status, groups.body)
		val shape = groups.json.list.map { aisle ->
			aisle.get("aisle").asString() to aisle.get("racks").list.map { "${it.get("rack").asString()}:${it.get("locations").asInt()}:${it.get("active").asInt()}" }
		}
		assertEquals(listOf("K" to listOf("05:3:3", "06:2:2"), "L" to listOf("05:1:1")), shape)
	}

	@Test
	fun `the warehouse's temporary location exists once, and cannot be deactivated`() {
		val warehouse = api.createWarehouse()
		val temporary = api.post("/locations/query", api.adminToken(), mapOf("filters" to mapOf("warehouseId" to warehouse, "temporary" to true)))
		assertEquals(1, temporary.json.get("total").asInt())
		val location = temporary.json.get("data").get(0)
		assertEquals("TEMPORARY", location.get("code").asString())
		assertTrue(location.get("temporary").asBoolean())
		assertEquals("SYSTEM_LOCATION_PROTECTED", api.delete("/locations/${location.get("id").asString()}", api.adminToken()).code)
	}

	@Test
	fun `a location is deactivated and reactivated, and never deleted`() {
		val warehouse = api.createWarehouse()
		val id = rack(warehouse, "M", "01", 1).json.get("locations").get(0).get("id").asString()
		val admin = api.adminToken()
		assertFalse(api.delete("/locations/$id", admin).json.get("active").asBoolean())
		assertEquals(listOf("M01A"), codes(mapOf("warehouseId" to warehouse, "active" to false)))
		assertTrue(api.post("/locations/$id/reactivate", admin).json.get("active").asBoolean())
		val log = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetType" to "location", "targetId" to id)))
		assertEquals(setOf("LOCATION_DEACTIVATED", "LOCATION_REACTIVATED"), log.json.get("data").list.map { it.get("action").asString() }.toSet())
	}

	@Test
	fun `with AREA locations of other warehouses are left out of the list, and not found by id`() {
		val mine = api.createWarehouse()
		val other = api.createWarehouse()
		rack(mine, "A", "01", 2)
		val foreign = rack(other, "A", "01", 1).json.get("locations").get(0).get("id").asString()
		val operator = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		api.put("/users/${operator.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(mine)))

		val page = api.post("/locations/query", operator.token, mapOf("filters" to mapOf("aisle" to "A", "rack" to "01"), "limit" to 200), "app")
		val seen = page.json.get("data").list.map { it.get("warehouseId").asString() }.toSet()
		assertEquals(setOf(mine), seen)
		assertEquals(page.json.get("data").size().toLong(), page.json.get("total").asLong())
		assertEquals("NOT_FOUND", api.call("GET", "/locations/$foreign", operator.token, channel = "app").code)
		assertEquals("NOT_FOUND", api.call("GET", "/warehouses/$other/location-groups", operator.token, channel = "app").code)
	}

	@Test
	fun `only locations manage creates racks and deactivates locations`() {
		val warehouse = api.createWarehouse()
		val manager = api.newUser("WAREHOUSE_MANAGER")
		val refused = api.post("/locations/racks", manager.token, mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05", "levels" to 1))
		assertEquals("INSUFFICIENT_PERMISSIONS", refused.code)
		val id = rack(warehouse, "K", "05", 1).json.get("locations").get(0).get("id").asString()
		assertEquals("INSUFFICIENT_PERMISSIONS", api.delete("/locations/$id", manager.token).code)
	}
}
