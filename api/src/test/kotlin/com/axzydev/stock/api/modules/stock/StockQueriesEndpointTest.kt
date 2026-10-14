package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

// F4-06 · e2e: stock by location and by material, with the groups by aisle and rack, inside the reader's warehouses.
class StockQueriesEndpointTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int): List<String> =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))
			.json.get("locations").list.map { it.get("id").asString() }

	private fun product(name: String) =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	private fun receive(location: String, product: String, quantity: Int) = stock.apply(
		Actor(null, "test"),
		listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), null, "test", UUID.randomUUID().toString())),
	)

	private fun rows(path: String, filters: Map<String, Any>, token: String = api.adminToken(), channel: String = "web") =
		api.post(path, token, mapOf("filters" to filters, "limit" to 200), channel).also { assertEquals(200, it.status, it.body) }.json

	@Test
	fun `stock by location lists each balance with its location and material, and filters by aisle and rack`() {
		val warehouse = api.createWarehouse()
		val (k05a, k05b) = rack(warehouse, "K", "05", 2)
		val l01a = rack(warehouse, "L", "01", 1).single()
		val screws = product("Tornillo")
		receive(k05a, screws, 300)
		receive(k05b, screws, 200)
		receive(l01a, screws, 50)

		val all = rows("/stock/balances/query", mapOf("warehouseId" to warehouse))
		assertEquals(listOf("K05A", "K05B", "L01A"), all.get("data").list.map { it.get("locationCode").asString() })
		assertEquals("Tornillo", all.get("data").get(0).get("productName").asString())
		assertEquals("Pieza", all.get("data").get(0).get("unit").get("label").asString())
		val rack = rows("/stock/balances/query", mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05"))
		assertEquals(2, rack.get("total").asInt())
	}

	@Test
	fun `stock by material adds up its balances in each warehouse`() {
		val warehouse = api.createWarehouse()
		val levels = rack(warehouse, "M", "01", 3)
		val nails = product("Clavo")
		receive(levels[0], nails, 80)
		receive(levels[2], nails, 20)

		val byProduct = rows("/stock/products/query", mapOf("warehouseId" to warehouse)).get("data").list.single()
		assertEquals(0, BigDecimal(100).compareTo(byProduct.get("quantity").decimalValue()))
		assertEquals(2, byProduct.get("locations").asInt())
		val balances = rows("/stock/balances/query", mapOf("warehouseId" to warehouse, "productId" to nails)).get("data").list
		assertEquals(0, byProduct.get("quantity").decimalValue().compareTo(balances.sumOf { it.get("quantity").decimalValue() }))
	}

	@Test
	fun `a reader with AREA sees only the stock of their warehouses`() {
		val mine = api.createWarehouse()
		val other = api.createWarehouse()
		val product = product("Arandela")
		receive(rack(mine, "A", "01", 1).single(), product, 5)
		receive(rack(other, "A", "01", 1).single(), product, 7)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(mine)))
		api.put("/users/${manager.id}/permissions/stock.view", api.adminToken(), mapOf("scope" to "AREA", "reason" to "Prueba de alcance"))

		val seen = rows("/stock/balances/query", mapOf("productId" to product), manager.token).get("data").list.map { it.get("warehouseId").asString() }.toSet()
		assertEquals(setOf(mine), seen)
		val totals = rows("/stock/products/query", mapOf("productId" to product), manager.token)
		assertEquals(1, totals.get("total").asInt())
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/stock/balances/query", api.newUser("OFFICE").token, mapOf<String, Any>()).code)
	}
}
