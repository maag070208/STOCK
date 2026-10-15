package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.core.time.BusinessTime
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestClock
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// F6-02 · e2e: the kardex of a material in a warehouse — an opening and three more movements, in order, with the running
// balance of the location and of the material, the filters by period, and the scope of the reader. Real PostgreSQL.
class KardexReportTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var clock: TestClock

	private val actor = Actor(null, "test")

	// The day the API works in: the kardex period is cut in the business zone, not in the machine's.
	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(BusinessTime.ZONE))

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int): List<String> =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))
			.json.get("locations").list.map { it.get("id").asString() }

	private fun product(name: String): String =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	private fun move(type: String, location: String, product: String, quantity: Int, reason: String? = null) = stock.apply(
		actor,
		listOf(MovementCommand(type, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), reason, "test", UUID.randomUUID().toString())),
	)

	private fun kardex(token: String = api.adminToken(), filters: Map<String, Any>): JsonNode =
		api.post("/reports/kardex", token, mapOf("filters" to filters, "limit" to 200)).also { assertEquals(200, it.status, it.body) }.json

	private fun rows(found: JsonNode): List<JsonNode> = found.get("data").list

	private fun running(found: JsonNode, field: String): List<String> = rows(found).map { it.get(field).decimalValue().stripTrailingZeros().toPlainString() }

	@Test
	fun `the kardex lists the movements in order with the running balance of the location and of the material`() {
		val warehouse = api.createWarehouse()
		val temporary = api.post("/locations/query", api.adminToken(), mapOf("filters" to mapOf("warehouseId" to warehouse, "temporary" to "true")))
			.json.get("data").list.single().get("id").asString()
		val (k05a, k05b) = rack(warehouse, "K", "05", 2)
		val screws = product("Tornillo")
		// The opening inventory of 10, two placements (5 and 2) and one pick of 3.
		move(StockService.OPENING, temporary, screws, 10, "Inventario inicial")
		move(StockService.RECEIPT, k05a, screws, 5)
		move(StockService.IN_RETURN, k05b, screws, 2)
		move(StockService.OUT, k05a, screws, 3, "Orden ORD-1")

		val found = kardex(filters = mapOf("productId" to screws, "warehouseId" to warehouse))
		assertEquals(4, found.get("total").asInt(), found.toString())
		assertEquals(listOf("OPENING", "RECEIPT", "IN_RETURN", "OUT"), rows(found).map { it.get("type").asString() })
		assertEquals(listOf("Inventario inicial", "Entrada", "Devolución a stock", "Salida"), rows(found).map { it.get("typeLabel").asString() })
		assertEquals(listOf("10", "15", "17", "14"), running(found, "runningWarehouse"))
		// Each location keeps its own balance: the opening leaves 10 in TEMPORARY, K05A takes 5 and then gives 3, and
		// K05B receives 2.
		assertEquals(listOf("10", "5", "2", "2"), running(found, "runningLocation"))
		assertEquals(listOf(temporary, k05a, k05b, k05a), rows(found).map { it.get("location").get("id").asString() })
		assertTrue(rows(found).all { it.get("incoming").asBoolean() == (it.get("type").asString() != "OUT") })
		assertEquals("Orden ORD-1", rows(found).get(3).get("reason").asString())
		// The last running balance is the current balance, and the kardex names its material and warehouse.
		assertEquals(0, BigDecimal(14).compareTo(found.get("product").get("closing").decimalValue()))
		assertEquals("Tornillo", found.get("product").get("product").get("name").asString())
		assertEquals(screws, found.get("product").get("product").get("id").asString())
	}

	@Test
	fun `a kardex over a period starts from what the material already had, and the scope is the reader's`() {
		val warehouse = api.createWarehouse()
		val (k05a, _) = rack(warehouse, "K", "05", 2)
		val screws = product("Tornillo")
		move(StockService.RECEIPT, k05a, screws, 10)
		move(StockService.OUT, k05a, screws, 4)

		// A period that starts after both movements: nothing is listed, and the closing balance is what the material had.
		val tomorrow = today().plusDays(1).toString()
		val later = kardex(filters = mapOf("productId" to screws, "warehouseId" to warehouse, "from" to tomorrow))
		assertEquals(0, later.get("total").asInt())
		assertEquals(0, BigDecimal(6).compareTo(later.get("product").get("closing").decimalValue()))

		// A kardex that starts before them lists both, ending at the balance the material has.
		val yesterday = today().minusDays(1).toString()
		val all = kardex(filters = mapOf("productId" to screws, "warehouseId" to warehouse, "from" to yesterday))
		assertEquals(2, all.get("total").asInt())
		assertEquals(listOf("10", "6"), running(all, "runningWarehouse"))

		// The filter of the material and the warehouse is required, and the scope is applied: another warehouse's
		// manager does not read this one.
		assertEquals("FIELD_REQUIRED", api.post("/reports/kardex", api.adminToken(), mapOf("filters" to mapOf("warehouseId" to warehouse))).code)
		assertEquals("FIELD_REQUIRED", api.post("/reports/kardex", api.adminToken(), mapOf("filters" to mapOf("productId" to screws))).code)
		val stranger = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		assertEquals(404, api.post("/reports/kardex", stranger.token, mapOf("filters" to mapOf("productId" to screws, "warehouseId" to warehouse))).status)
	}
}
