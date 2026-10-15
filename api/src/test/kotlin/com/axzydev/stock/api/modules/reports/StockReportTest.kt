package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.UUID

// F6-01 · e2e: the current stock report, with its totals by warehouse and by aisle and rack, its filters, the materials
// below their minimum, and the scope of the reader. Run against real PostgreSQL.
class StockReportTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int): List<String> =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))
			.json.get("locations").list.map { it.get("id").asString() }

	private fun product(name: String, minimum: Int = 0): String =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE", "minimum" to minimum)).text("id")

	private fun receive(location: String, product: String, quantity: Int) = stock.apply(
		actor,
		listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), null, "test", UUID.randomUUID().toString())),
	)

	// The report with these filters, as the screen asks for it.
	private fun report(token: String = api.adminToken(), filters: Map<String, Any> = emptyMap(), limit: Int = 200): JsonNode =
		api.post("/reports/stock", token, mapOf("filters" to filters, "limit" to limit)).also { assertEquals(200, it.status, it.body) }.json

	private fun manager(warehouse: String): TestUser =
		api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(warehouse))) }

	@Test
	fun `the report lists the balances with their totals by warehouse and by aisle and rack`() {
		val warehouse = api.createWarehouse()
		val (k05a, k05b) = rack(warehouse, "K", "05", 2)
		val l01a = rack(warehouse, "L", "01", 1).single()
		val screws = product("Tornillo")
		val nails = product("Clavo")
		receive(k05a, screws, 300)
		receive(k05b, screws, 200)
		receive(l01a, nails, 50)

		val found = report(filters = mapOf("warehouseId" to warehouse))
		assertEquals(3, found.get("total").asInt())
		assertEquals(listOf("K05A", "K05B", "L01A"), found.get("data").list.map { it.get("location").get("code").asString() })
		val first = found.get("data").get(0)
		assertEquals("Tornillo", first.get("product").get("name").asString())
		assertEquals("Pieza", first.get("unit").get("label").asString())
		assertEquals(0, BigDecimal(550).compareTo(found.get("quantity").decimalValue()))

		// The groups add up to the warehouse, and the warehouses to the whole report.
		val groups = found.get("groupTotals").list
		assertEquals(listOf("K05", "L01"), groups.map { it.get("key").asString() })
		assertEquals(0, BigDecimal(550).compareTo(groups.fold(BigDecimal.ZERO) { sum, group -> sum.add(group.get("quantity").decimalValue()) }))
		val warehouses = found.get("warehouseTotals").list
		val code = found.get("data").get(0).get("warehouse").get("code").asString()
		assertEquals(listOf(code), warehouses.map { it.get("key").asString() })
		assertEquals("Almacén de prueba", warehouses.single().get("label").asString())
		assertEquals(0, BigDecimal(550).compareTo(warehouses.single().get("quantity").decimalValue()))
		assertEquals(3L, warehouses.single().get("rows").asLong())

		// The filters of the screen.
		assertEquals(2, report(filters = mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05")).get("total").asInt())
		assertEquals(1, report(filters = mapOf("warehouseId" to warehouse, "location" to "L01")).get("total").asInt())
		assertEquals(2, report(filters = mapOf("warehouseId" to warehouse, "product" to "tornillo")).get("total").asInt())
		assertEquals("UNKNOWN_FIELD", api.post("/reports/stock", api.adminToken(), mapOf("filters" to mapOf("nope" to "x"))).code)
	}

	@Test
	fun `the materials below their minimum are listed with what is missing`() {
		val warehouse = api.createWarehouse()
		val (k05a, k05b) = rack(warehouse, "K", "05", 2)
		val scarce = product("Arandela", minimum = 5)
		val plenty = product("Tuerca", minimum = 5)
		receive(k05a, scarce, 2)
		receive(k05b, scarce, 1)
		receive(k05a, plenty, 40)

		// Without the filter every balance is listed, and only the rows of a material that is below carry a shortfall.
		val all = report(filters = mapOf("warehouseId" to warehouse))
		assertEquals(3, all.get("total").asInt())
		assertTrue(all.get("data").list.filter { it.get("product").get("name").asString() == "Arandela" }.all { !it.get("shortfall").isNull })
		assertTrue(all.get("data").list.filter { it.get("product").get("name").asString() == "Tuerca" }.all { it.get("shortfall").isNull })

		val below = report(filters = mapOf("warehouseId" to warehouse, "belowMinimum" to "true"))
		assertEquals(2, below.get("total").asInt(), below.toString())
		assertTrue(below.get("data").list.all { it.get("product").get("name").asString() == "Arandela" })
		// The material has 3 over the warehouses and its minimum is 5: two are missing.
		assertTrue(below.get("data").list.all { 0 == BigDecimal(2).compareTo(it.get("shortfall").decimalValue()) })
		assertEquals(0, BigDecimal(3).compareTo(below.get("quantity").decimalValue()))
	}

	@Test
	fun `a reader with AREA sees only their warehouses, and the rest answers as if it did not exist`() {
		val mine = api.createWarehouse()
		val other = api.createWarehouse()
		val product = product("Birlo")
		receive(rack(mine, "A", "01", 1).single(), product, 5)
		receive(rack(other, "A", "01", 1).single(), product, 7)
		val manager = manager(mine)
		// A manager of another warehouse, with AREA, sees nothing of these two.
		val stranger = manager(api.createWarehouse())

		val seen = report(manager.token, mapOf("product" to "Birlo"))
		assertEquals(1, seen.get("total").asInt())
		assertEquals(listOf(seen.get("data").get(0).get("warehouse").get("code").asString()), seen.get("warehouseTotals").list.map { it.get("key").asString() })
		assertEquals(0, BigDecimal(5).compareTo(seen.get("quantity").decimalValue()))

		// The warehouse of another manager is out of scope: 404, never an empty list.
		assertEquals(404, api.post("/reports/stock", stranger.token, mapOf("filters" to mapOf("warehouseId" to mine))).status)
		// A warehouse that does not exist answers the same way.
		assertEquals(404, api.post("/reports/stock", stranger.token, mapOf("filters" to mapOf("warehouseId" to UUID.randomUUID().toString()))).status)
		// And someone without reports.view cannot read one at all.
		val operator = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		assertEquals(403, api.call("POST", "/reports/stock", operator.token, mapOf<String, Any>(), channel = "app").status)
		val appUser = api.newUser("APP_USER", channel = "app")
		assertEquals(403, api.call("POST", "/reports/stock", appUser.token, mapOf<String, Any>(), channel = "app").status)
	}
}
