package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.time.BusinessTime
import com.axzydev.stock.api.modules.entries.EntriesFixture
import com.axzydev.stock.api.modules.orders.OrdersFixture
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestClock
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// F6-04 · e2e: the orders report — what was asked, picked and moved, by period and status — and the entries report, with
// the discrepancies of its invoices. Run against real PostgreSQL.
class SalesReportTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var clock: TestClock

	private val actor = Actor(null, "test")

	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(BusinessTime.ZONE))

	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	private fun ordersReport(filters: Map<String, Any>, token: String = api.adminToken()): JsonNode =
		api.post("/reports/orders", token, mapOf("filters" to filters, "limit" to 200)).also { assertEquals(200, it.status, it.body) }.json

	private fun invoicesReport(filters: Map<String, Any>, token: String = api.adminToken()): JsonNode =
		api.post("/reports/invoices", token, mapOf("filters" to filters, "limit" to 200)).also { assertEquals(200, it.status, it.body) }.json

	private fun pick(operator: TestUser, order: JsonNode, line: JsonNode, location: String, quantity: Int) =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/picks", operator.token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity), channel = "app",
		)

	private fun rowOf(found: JsonNode, number: String): JsonNode = found.get("data").list.single { it.get("order").get("code").asString() == number }

	@Test
	fun `the orders report answers what was asked, picked and moved, by period and status`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val source = f.create(f.body(f.line(product, 30))).json
		val line = source.get("lines").get(0)
		api.put("/orders/${source.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))
		pick(f.operators[0], source, line, f.locations[0], 20)
		// The 20 picked units move to another order that needs them, and the source can be cancelled.
		val target = f.create(f.body(f.line(product, 20))).json
		val moved = api.post(
			"/orders/${source.get("id").asString()}/reassignments/transfer", f.admin,
			mapOf("lineId" to line.get("id").asString(), "targetOrderId" to target.get("id").asString(), "quantity" to 20, "note" to "Pasa a la otra"),
		)
		assertEquals(200, moved.status, moved.body)

		val found = ordersReport(mapOf("warehouseId" to f.warehouse))
		assertEquals(2, found.get("total").asInt(), found.toString())
		val sourceRow = rowOf(found, source.get("number").asString())
		assertEquals(0, BigDecimal(30).compareTo(sourceRow.get("asked").decimalValue()))
		assertEquals(0, BigDecimal.ZERO.compareTo(sourceRow.get("picked").decimalValue()))
		assertEquals(0, BigDecimal(20).compareTo(sourceRow.get("movedOut").decimalValue()))
		assertEquals("FULFILLING", sourceRow.get("status").asString())
		assertEquals("Surtiendo", sourceRow.get("statusLabel").asString())
		val targetRow = rowOf(found, target.get("number").asString())
		assertEquals(0, BigDecimal(20).compareTo(targetRow.get("picked").decimalValue()))
		assertEquals(0, BigDecimal(20).compareTo(targetRow.get("movedIn").decimalValue()))
		// The totals add up, and each status carries its picked quantity.
		assertEquals(0, BigDecimal(50).compareTo(found.get("asked").decimalValue()))
		assertEquals(0, BigDecimal(20).compareTo(found.get("picked").decimalValue()))
		assertEquals(0, BigDecimal(20).compareTo(found.get("movedOut").decimalValue()))
		assertEquals(0, BigDecimal(20).compareTo(found.get("movedIn").decimalValue()))
		assertEquals(listOf("FULFILLED", "FULFILLING"), found.get("statusTotals").list.map { it.get("key").asString() })
		assertEquals(0, BigDecimal(20).compareTo(found.get("statusTotals").list.single { it.get("key").asString() == "FULFILLED" }.get("quantity").decimalValue()))

		// The period: both orders were created today.
		assertEquals(2, ordersReport(mapOf("warehouseId" to f.warehouse, "from" to today().toString(), "to" to today().toString())).get("total").asInt())
		assertEquals(0, ordersReport(mapOf("warehouseId" to f.warehouse, "from" to today().plusDays(1).toString())).get("total").asInt())
		// The status, one code or several.
		assertEquals(listOf(target.get("number").asString()), ordersReport(mapOf("warehouseId" to f.warehouse, "status" to "FULFILLED")).get("data").list.map { it.get("order").get("code").asString() })
		assertEquals(2, ordersReport(mapOf("warehouseId" to f.warehouse, "status" to listOf("FULFILLING", "FULFILLED"))).get("total").asInt())
		// The customer filter, and the scope.
		assertEquals(2, ordersReport(mapOf("warehouseId" to f.warehouse, "customerId" to f.customer)).get("total").asInt())
		assertEquals(0, ordersReport(mapOf("warehouseId" to f.warehouse, "customerId" to UUID.randomUUID().toString())).get("total").asInt())
		val stranger = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		assertEquals(404, api.post("/reports/orders", stranger.token, mapOf("filters" to mapOf("warehouseId" to f.warehouse))).status)
	}

	@Test
	fun `the entries report keeps the discrepancies of the invoices`() {
		val f = EntriesFixture(api)
		val operator = f.operators[0]
		val nails = f.product("Clavo")
		val invoice = f.create(f.body(f.line(nails, 100, operator to 100))).json
		val assignment = invoice.get("lines").get(0).get("assignments").get(0).get("id").asString()
		// The operator counts 90 of 100: a difference the office settles.
		val counted = api.call("POST", "/receipts/assignments/$assignment/count", operator.token, mapOf("countedQuantity" to 90), channel = "app")
		assertEquals(200, counted.status, counted.body)
		assertEquals("DISCREPANCY", counted.text("outcome"))
		assertEquals(200, api.post("/receipts/assignments/$assignment/adjustment", f.admin, mapOf("note" to "Llegaron 90")).status)

		val found = invoicesReport(mapOf("warehouseId" to f.warehouse))
		assertEquals(1, found.get("total").asInt(), found.toString())
		val row = found.get("data").get(0)
		assertEquals(invoice.get("number").asString(), row.get("invoice").get("code").asString())
		// The count settled the difference, so the invoice moved on to its placement.
		assertEquals("IN_ASSIGNMENT", row.get("status").asString())
		assertEquals(0, BigDecimal(90).compareTo(row.get("expected").decimalValue()))
		assertEquals(0, BigDecimal(90).compareTo(row.get("counted").decimalValue()))
		assertEquals(0, BigDecimal.ZERO.compareTo(row.get("difference").decimalValue()))
		assertEquals(0, BigDecimal(90).compareTo(found.get("expected").decimalValue()))
		assertEquals(0, BigDecimal(90).compareTo(found.get("counted").decimalValue()))

		// The period is the invoice's date, and the status filter takes the catalog's codes.
		assertEquals(1, invoicesReport(mapOf("warehouseId" to f.warehouse, "from" to today().toString(), "to" to today().toString())).get("total").asInt())
		assertEquals(0, invoicesReport(mapOf("warehouseId" to f.warehouse, "from" to today().minusDays(1).toString(), "to" to today().minusDays(1).toString())).get("total").asInt())
		assertEquals(1, invoicesReport(mapOf("warehouseId" to f.warehouse, "status" to listOf(row.get("status").asString()))).get("total").asInt())
		assertEquals(0, invoicesReport(mapOf("warehouseId" to f.warehouse, "status" to "CLOSED")).get("total").asInt())
		assertEquals(1, invoicesReport(mapOf("warehouseId" to f.warehouse, "supplierId" to f.supplier)).get("total").asInt())
		// The scope is the same as every other report: another manager's warehouse answers 404, not an empty list.
		val stranger = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		assertEquals(404, api.post("/reports/invoices", stranger.token, mapOf("filters" to mapOf("warehouseId" to f.warehouse))).status)
	}
}
