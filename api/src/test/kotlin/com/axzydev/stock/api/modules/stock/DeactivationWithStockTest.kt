package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

// F4-05 · e2e: a location with stock cannot be deactivated, nor a rack with one level that has stock (deferred from F2).
class DeactivationWithStockTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	private fun move(type: String, location: String, product: String, quantity: Int) =
		stock.apply(actor, listOf(MovementCommand(type, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), null, "test", UUID.randomUUID().toString())))

	@Test
	fun `a location with stock answers 409, and once the stock leaves it is deactivated`() {
		val warehouse = api.createWarehouse()
		val location = api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to "B", "rack" to "02", "levels" to 1)).json.get("locations").get(0).get("id").asString()
		val product = api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to "Clavo", "unit" to "PIECE")).text("id")
		move(StockService.RECEIPT, location, product, 5)

		val refused = api.delete("/locations/$location", api.adminToken())
		assertEquals(409, refused.status)
		assertEquals("LOCATION_HAS_STOCK", refused.code)
		move(StockService.OUT, location, product, 5)
		assertEquals(false, api.delete("/locations/$location", api.adminToken()).json.get("active").asBoolean())
	}

	@Test
	fun `a rack deactivates its levels only when every one is empty`() {
		val admin = api.adminToken()
		val warehouse = api.createWarehouse()
		val levels = api.post("/locations/racks", admin, mapOf("warehouseId" to warehouse, "aisle" to "C", "rack" to "03", "levels" to 3)).json.get("locations").list.map { it.get("id").asString() }
		val product = api.post("/products", admin, mapOf("code" to uniqueCode("P").replace("-", ""), "name" to "Clavo", "unit" to "PIECE")).text("id")
		move(StockService.RECEIPT, levels[1], product, 2)

		val refused = api.delete("/warehouses/$warehouse/racks/C/03", admin)
		assertEquals("LOCATION_HAS_STOCK", refused.code)
		move(StockService.OUT, levels[1], product, 2)
		val done = api.delete("/warehouses/$warehouse/racks/C/03", admin)
		assertEquals(200, done.status, done.body)
		assertTrue(done.json.get("locations").list.none { it.get("active").asBoolean() })
		val groups = api.get("/warehouses/$warehouse/location-groups", admin).json.list.single().get("racks").list.single()
		assertEquals(0, groups.get("active").asInt())
		assertEquals("NOT_FOUND", api.delete("/warehouses/$warehouse/racks/Z/99", admin).code)
	}
}
