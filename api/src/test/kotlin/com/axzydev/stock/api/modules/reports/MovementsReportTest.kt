package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.core.time.BusinessTime
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestClock
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// F6-03 · e2e: the movements report — the period, the type, the user and the warehouse combine with AND, the totals add
// up, and a warehouse outside the reader's scope answers 404. Run against real PostgreSQL.
class MovementsReportTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var clock: TestClock

	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(BusinessTime.ZONE))

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int): List<String> =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))
			.json.get("locations").list.map { it.get("id").asString() }

	private fun product(name: String): String =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	private fun move(type: String, location: String, product: String, quantity: Int, by: TestUser?) = stock.apply(
		by?.let { Actor(UUID.fromString(it.id), it.username) } ?: Actor(null, "test"),
		listOf(MovementCommand(type, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), null, "test", UUID.randomUUID().toString())),
	)

	private fun report(filters: Map<String, Any>, token: String = api.adminToken(), sort: Map<String, String>? = null): JsonNode =
		api.post("/reports/movements", token, mapOf("filters" to filters, "limit" to 200, "sort" to sort))
			.also { assertEquals(200, it.status, it.body) }.json

	private fun types(found: JsonNode): List<String> = found.get("data").list.map { it.get("type").asString() }

	@Test
	fun `the period, the type and the user combine with AND, and the totals add up`() {
		val warehouse = api.createWarehouse()
		val (k05a, _) = rack(warehouse, "K", "05", 2)
		val screws = product("Tornillo")
		val ana = api.newUser("WAREHOUSE_MANAGER")
		val beto = api.newUser("WAREHOUSE_MANAGER")
		move(StockService.RECEIPT, k05a, screws, 10, ana)
		move(StockService.OUT, k05a, screws, 4, beto)
		// A movement of tomorrow: today's report must not list it.
		clock.after(Duration.ofDays(1)) { move(StockService.RECEIPT, k05a, screws, 2, ana) }

		val all = report(mapOf("warehouseId" to warehouse))
		assertEquals(3, all.get("total").asInt())
		assertEquals(0, BigDecimal(12).compareTo(all.get("incoming").decimalValue()))
		assertEquals(0, BigDecimal(4).compareTo(all.get("outgoing").decimalValue()))
		assertEquals(listOf("OUT", "RECEIPT"), all.get("typeTotals").list.map { it.get("key").asString() })

		// One day: only what happened today.
		val oneDay = report(mapOf("warehouseId" to warehouse, "from" to today().toString(), "to" to today().toString()))
		assertEquals(2, oneDay.get("total").asInt(), oneDay.toString())
		assertEquals(listOf("OUT", "RECEIPT"), types(oneDay).sorted())

		// One day and one type, with the user on top of it: every filter must match.
		assertEquals(1, report(mapOf("warehouseId" to warehouse, "from" to today().toString(), "type" to "OUT")).get("total").asInt())
		assertEquals(listOf("OUT"), types(report(mapOf("warehouseId" to warehouse, "type" to "out"))))
		assertEquals(2, report(mapOf("warehouseId" to warehouse, "userId" to ana.id)).get("total").asInt())
		assertEquals(1, report(mapOf("warehouseId" to warehouse, "userId" to ana.id, "type" to "RECEIPT", "from" to today().toString(), "to" to today().toString())).get("total").asInt())
		assertEquals(0, report(mapOf("warehouseId" to warehouse, "userId" to beto.id, "type" to "RECEIPT")).get("total").asInt())

		// The report lists the most recent first, and its rows carry the location and the material: today's last
		// movement is the pick of four.
		val first = oneDay.get("data").get(0)
		assertEquals("K05A", first.get("location").get("code").asString())
		assertEquals("Tornillo", first.get("product").get("name").asString())
		assertEquals("Salida", first.get("typeLabel").asString())
	}

	@Test
	fun `a warehouse outside the scope answers 404, and nothing of it is listed`() {
		val mine = api.createWarehouse()
		val other = api.createWarehouse()
		val product = product("Birlo")
		move(StockService.RECEIPT, rack(mine, "A", "01", 1).single(), product, 5, null)
		move(StockService.RECEIPT, rack(other, "A", "01", 1).single(), product, 7, null)
		val manager = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(mine))) }
		val stranger = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(other))) }

		// The manager sees their warehouse and nothing else, and the totals are over their rows only.
		val seen = report(mapOf("productId" to product), manager.token)
		assertEquals(1, seen.get("total").asInt())
		assertEquals(0, BigDecimal(5).compareTo(seen.get("incoming").decimalValue()))
		// Asking for a warehouse that is not theirs, or one that does not exist, is 404.
		assertEquals(404, api.post("/reports/movements", stranger.token, mapOf("filters" to mapOf("warehouseId" to mine))).status)
		assertEquals(404, api.post("/reports/movements", stranger.token, mapOf("filters" to mapOf("warehouseId" to UUID.randomUUID().toString()))).status)
		// The stranger reads their own warehouse, and only it: the manager's is out of scope.
		val own = report(mapOf("productId" to product, "warehouseId" to other), stranger.token, sort = mapOf("key" to "date", "direction" to "asc"))
		assertEquals(1, own.get("total").asInt())
		assertEquals(0, BigDecimal(7).compareTo(own.get("incoming").decimalValue()))
	}
}
