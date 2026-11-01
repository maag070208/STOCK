package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// F4-14 to F4-18 · e2e: counts, adjustments, the status machine, placement and closing, with F4 criteria 3 to 12.
class ReceivingEndpointTest : ApiTest() {

	@Autowired
	lateinit var jdbc: JdbcTemplate

	private fun invoice(f: EntriesFixture, vararg lines: Map<String, Any?>): JsonNode = f.create(f.body(*lines)).also { assertEquals(201, it.status, it.body) }.json

	// The assignments of an invoice, by operator and material code.
	private fun assignment(invoice: JsonNode, operator: TestUser, line: Int = 0): String =
		invoice.get("lines").get(line).get("assignments").list.single { it.get("operator").get("id").asString() == operator.id }.get("id").asString()

	private fun refresh(f: EntriesFixture, invoice: JsonNode) = api.get("/invoices/${invoice.get("id").asString()}", f.admin).json

	private fun status(f: EntriesFixture, invoice: JsonNode) = refresh(f, invoice).get("status").get("code").asString()

	private fun count(operator: TestUser, assignment: String, quantity: Int, notes: String? = null, key: String = java.util.UUID.randomUUID().toString()) =
		api.call(
			"POST", "/receipts/assignments/$assignment/count", operator.token, mapOf("countedQuantity" to quantity, "notes" to notes),
			channel = "app", headers = mapOf("Idempotency-Key" to key),
		)

	private fun place(operator: TestUser, assignment: String, vararg placements: Pair<String, Int>) =
		placeWithKey(java.util.UUID.randomUUID().toString(), operator, assignment, *placements)

	private fun placeWithKey(key: String, operator: TestUser, assignment: String, vararg placements: Pair<String, Int>) =
		api.call(
			"POST", "/receipts/assignments/$assignment/placements", operator.token,
			mapOf("placements" to placements.map { mapOf("locationId" to it.first, "quantity" to it.second) }),
			channel = "app", headers = mapOf("Idempotency-Key" to key),
		)

	private fun adjust(token: String, assignment: String, note: String? = "Llegó distinto") =
		api.post("/receipts/assignments/$assignment/adjustment", token, mapOf("note" to note))

	private fun balance(location: String, product: String): BigDecimal =
		jdbc.queryForList("select quantity from stock_balances where location_id = ?::uuid and product_id = ?::uuid", BigDecimal::class.java, location, product).singleOrNull() ?: BigDecimal.ZERO

	@Test
	fun `a count of 500 for 500 is a match, and when everything is counted the invoice moves to IN_ASSIGNMENT`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val product = f.product()
		val inv = invoice(f, f.line(product, 500, a to 500))
		val counted = count(a, assignment(inv, a), 500, "Completo")
		assertEquals(200, counted.status, counted.body)
		assertEquals("MATCH", counted.text("outcome"))
		assertEquals(0, counted.json.get("difference").asInt())
		assertEquals("IN_ASSIGNMENT", status(f, inv))
		// The operator's list groups their assignments by invoice.
		val mine = api.call("GET", "/receipts/mine", a.token, channel = "app").json.list
		assertTrue(mine.any { it.get("invoice").get("id").asString() == inv.get("id").asString() })
		val filtered = api.call("GET", "/receipts/mine?productId=$product", a.token, channel = "app").json.list
		assertEquals(listOf(product), filtered.single().get("lines").list.map { it.get("product").get("id").asString() })
		assertEquals(0, api.call("GET", "/receipts/mine?productId=${java.util.UUID.randomUUID()}", a.token, channel = "app").json.list.size)
	}

	@Test
	fun `differences accept the counted quantity without rewriting the supplier invoice`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val inv = invoice(f, f.line(f.product("Clavo"), 100, a to 100), f.line(f.product("Alambre"), 120, a to 120))
		val nails = inv.get("lines").list.single { it.get("quantity").asInt() == 100 }.get("assignments").get(0).get("id").asString()
		val wire = inv.get("lines").list.single { it.get("quantity").asInt() == 120 }.get("assignments").get(0).get("id").asString()
		assertEquals(-20, count(a, nails, 80).json.get("difference").asInt())
		assertEquals(60, count(a, wire, 180).json.get("difference").asInt())
		assertEquals("PENDING", status(f, inv))

		val office = api.newUser("OFFICE")
		assertEquals("FIELD_REQUIRED", adjust(office.token, nails, note = " ").code)
		val adjusted = adjust(office.token, nails)
		assertEquals(200, adjusted.status, adjusted.body)
		assertEquals(100, adjusted.json.get("expectedQuantity").asInt())
		assertEquals(80, adjusted.json.get("acceptedQuantity").asInt())
		assertEquals("PENDING", status(f, inv))
		adjust(office.token, wire)
		val after = refresh(f, inv)
		assertEquals(setOf(100, 120), after.get("lines").list.map { it.get("quantity").asInt() }.toSet())
		assertEquals(setOf(80, 180), after.get("lines").list.flatMap { it.get("assignments").list }.map { it.get("acceptedQuantity").asInt() }.toSet())
		assertEquals("IN_ASSIGNMENT", after.get("status").get("code").asString())

		val log = api.post("/audit-log/query", f.admin, mapOf("filters" to mapOf("action" to "DISCREPANCY_ADJUSTED", "targetId" to nails))).json.get("data").single()
		assertEquals(100, log.get("before").get("expectedQuantity").asInt())
		assertEquals(100, log.get("before").get("lineQuantity").asInt())
		assertEquals(80, log.get("after").get("acceptedQuantity").asInt())
		assertEquals(100, log.get("after").get("lineQuantity").asInt())
		assertEquals(-20, log.get("after").get("difference").asInt())
		assertEquals("Llegó distinto", log.get("after").get("note").asString())
	}

	@Test
	fun `a split line keeps its original expected quantities and totals the accepted ones`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val inv = invoice(f, f.line(f.product(), 100, a to 60, b to 40))
		count(a, assignment(inv, a), 50)
		count(b, assignment(inv, b), 40)
		adjust(f.admin, assignment(inv, a))
		val line = refresh(f, inv).get("lines").get(0)
		assertEquals(100, line.get("quantity").asInt())
		assertEquals(100, line.get("assignments").list.sumOf { it.get("expectedQuantity").asInt() })
		assertEquals(90, line.get("assignments").list.sumOf { it.get("acceptedQuantity").asInt() })
	}

	@Test
	fun `an operator counts and places only their own assignments`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val inv = invoice(f, f.line(f.product(), 10, a to 10))
		assertEquals("NOT_FOUND", count(b, assignment(inv, a), 10).code)
		assertEquals("QUANTITY_INVALID", count(a, assignment(inv, a), -1).code)
	}

	@Test
	fun `500 screws placed as 300 in K05A and 200 in K05B make two receipts and raise both balances, and the invoice closes`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val screws = f.product()
		val inv = invoice(f, f.line(screws, 500, a to 500))
		val id = assignment(inv, a)
		// Before counting, nothing can be placed.
		assertEquals("ASSIGNMENT_NOT_SETTLED", place(a, id, f.locations[0] to 500).code)
		count(a, id, 500)
		val first = place(a, id, f.locations[0] to 300)
		assertEquals(200, first.status, first.body)
		assertEquals("IN_ASSIGNMENT", status(f, inv))
		// Criterion 9: more than was counted is refused.
		assertEquals("PLACEMENT_EXCEEDS_COUNT", place(a, id, f.locations[1] to 300).code)
		val second = place(a, id, f.locations[1] to 200)
		assertTrue(second.json.get("placed").asBoolean())
		assertEquals(0, BigDecimal(300).compareTo(balance(f.locations[0], screws)))
		assertEquals(0, BigDecimal(200).compareTo(balance(f.locations[1], screws)))
		assertEquals(2, jdbc.queryForObject("select count(*) from stock_movements where source_type = 'receipt_assignment' and source_id = ?", Int::class.java, id))
		// Criterion 12: the last placement closes the invoice, and a closed invoice cannot change.
		assertEquals("CLOSED", status(f, inv))
		assertEquals("INVOICE_LOCKED", api.post("/invoices/${inv.get("id").asString()}/lines", f.admin, mapOf("productId" to f.product(), "quantity" to 1)).code)
	}

	@Test
	fun `a placement goes only into locations of the invoice's warehouse, and never into TEMPORARY`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val inv = invoice(f, f.line(f.product(), 5, a to 5))
		val id = assignment(inv, a)
		count(a, id, 5)
		val elsewhere = api.post("/locations/racks", f.admin, mapOf("warehouseId" to api.createWarehouse(), "aisle" to "Z", "rack" to "01", "levels" to 1)).json.get("locations").get(0).get("id").asString()
		assertEquals("LOCATION_NOT_IN_WAREHOUSE", place(a, id, elsewhere to 5).code)
		val temporary = api.get("/warehouses/${f.warehouse}", f.admin).json.get("temporaryLocation").get("id").asString()
		assertEquals("CATALOG_VALUE_INVALID", place(a, id, temporary to 5).code)
	}

	@Test
	fun `two operators placing into the same location at the same time both count`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val screws = f.product()
		val inv = invoice(f, f.line(screws, 200, a to 100, b to 100))
		count(a, assignment(inv, a), 100)
		count(b, assignment(inv, b), 100)
		val pool = Executors.newFixedThreadPool(2)
		val start = CountDownLatch(1)
		val results = listOf(a, b).map { operator -> pool.submit<Int> { start.await(); place(operator, assignment(inv, operator), f.locations[0] to 100).status } }
		start.countDown()
		assertEquals(listOf(200, 200), results.map { it.get(30, TimeUnit.SECONDS) })
		pool.shutdown()
		assertEquals(0, BigDecimal(200).compareTo(balance(f.locations[0], screws)))
		assertEquals("CLOSED", status(f, inv))
	}

	@Test
	fun `a manager resolves the discrepancies of their warehouse only, and they are listed`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val inv = invoice(f, f.line(f.product(), 10, a to 10))
		val id = assignment(inv, a)
		count(a, id, 7)
		val listed = api.post("/receipts/discrepancies/query", f.admin, mapOf("filters" to mapOf("warehouseId" to f.warehouse))).json.get("data").list
		assertEquals(listOf(-3), listed.map { it.get("difference").asInt() })
		val stranger = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${stranger.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(api.createWarehouse())))
		assertEquals("INSUFFICIENT_PERMISSIONS", adjust(stranger.token, id).code)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse)))
		assertEquals(200, adjust(manager.token, id).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", adjust(a.token, id).code)
	}

	@Test
	fun `a settled line can be placed while another line still needs a count`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val ready = f.product("Lista para ubicar")
		val waiting = f.product("Pendiente de contar")
		val inv = invoice(f, f.line(ready, 10, a to 10), f.line(waiting, 20, b to 20))
		val readyId = inv.get("lines").list.single { it.get("product").get("id").asString() == ready }.get("assignments").get(0).get("id").asString()
		val waitingId = inv.get("lines").list.single { it.get("product").get("id").asString() == waiting }.get("assignments").get(0).get("id").asString()
		count(a, readyId, 10)
		assertEquals("PENDING", status(f, inv))
		assertEquals(200, place(a, readyId, f.locations[0] to 10).status)
		assertEquals(0, BigDecimal.TEN.compareTo(balance(f.locations[0], ready)))
		assertEquals("PENDING", status(f, inv))
		count(b, waitingId, 20)
		assertEquals("IN_ASSIGNMENT", status(f, inv))
		assertEquals(200, place(b, waitingId, f.locations[1] to 20).status)
		assertEquals("CLOSED", status(f, inv))
	}

	@Test
	fun `a discrepancy blocks only its own assignment and an adjusted placement keeps the original quantities`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val short = f.product("Faltante")
		val ready = f.product("Sin diferencia")
		val inv = invoice(f, f.line(short, 100, a to 100), f.line(ready, 10, b to 10))
		val shortLine = inv.get("lines").list.single { it.get("product").get("id").asString() == short }
		val shortId = shortLine.get("assignments").get(0).get("id").asString()
		val readyId = inv.get("lines").list.single { it.get("product").get("id").asString() == ready }.get("assignments").get(0).get("id").asString()
		count(a, shortId, 80)
		count(b, readyId, 10)
		assertEquals("ASSIGNMENT_NOT_SETTLED", place(a, shortId, f.locations[0] to 80).code)
		assertEquals(200, place(b, readyId, f.locations[0] to 10).status)
		val adjusted = adjust(f.admin, shortId)
		assertEquals(100, adjusted.json.get("expectedQuantity").asInt())
		assertEquals(80, adjusted.json.get("acceptedQuantity").asInt())
		assertEquals("DISCREPANCY", adjusted.text("outcome"))
		assertEquals(100, refresh(f, inv).get("lines").list.single { it.get("id").asString() == shortLine.get("id").asString() }.get("quantity").asInt())
		assertEquals(200, place(a, shortId, f.locations[1] to 80).status)
	}

	@Test
	fun `a placement retry returns the first answer and writes one movement`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val product = f.product()
		val inv = invoice(f, f.line(product, 10, a to 10))
		val id = assignment(inv, a)
		count(a, id, 10)
		val key = java.util.UUID.randomUUID().toString()
		val first = placeWithKey(key, a, id, f.locations[0] to 4)
		val retry = placeWithKey(key, a, id, f.locations[0] to 4)
		assertEquals(200, retry.status, retry.body)
		assertEquals(first.body, retry.body)
		assertEquals(1, jdbc.queryForObject("select count(*) from stock_movements where source_type = 'receipt_assignment' and source_id = ?", Int::class.java, id))
		assertEquals(0, BigDecimal(4).compareTo(balance(f.locations[0], product)))
		assertEquals("IDEMPOTENCY_KEY_REUSED", placeWithKey(key, a, id, f.locations[0] to 5).code)
		val second = place(a, id, f.locations[1] to 6)
		assertEquals(200, second.status, second.body)
		assertTrue(second.json.get("placed").asBoolean())
	}

	@Test
	fun `concurrent retries of one placement write one movement`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val product = f.product()
		val inv = invoice(f, f.line(product, 10, a to 10))
		val id = assignment(inv, a)
		count(a, id, 10)
		val key = java.util.UUID.randomUUID().toString()
		val pool = Executors.newFixedThreadPool(2)
		val start = CountDownLatch(1)
		val results = List(2) { pool.submit<Int> { start.await(); placeWithKey(key, a, id, f.locations[0] to 10).status } }
		start.countDown()
		assertEquals(listOf(200, 200), results.map { it.get(30, TimeUnit.SECONDS) })
		pool.shutdown()
		assertEquals(1, jdbc.queryForObject("select count(*) from stock_movements where source_type = 'receipt_assignment' and source_id = ?", Int::class.java, id))
		assertEquals(0, BigDecimal.TEN.compareTo(balance(f.locations[0], product)))
	}

	@Test
	fun `count and placement require an idempotency key`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val inv = invoice(f, f.line(f.product(), 10, a to 10))
		val id = assignment(inv, a)
		val missingCount = api.call("POST", "/receipts/assignments/$id/count", a.token, mapOf("countedQuantity" to 10), channel = "app")
		assertEquals("FIELD_REQUIRED", missingCount.code)
		val missingPlacement = api.call("POST", "/receipts/assignments/$id/placements", a.token, mapOf("placements" to listOf(mapOf("locationId" to f.locations[0], "quantity" to 10))), channel = "app")
		assertEquals("FIELD_REQUIRED", missingPlacement.code)
	}

	@Test
	fun `a count cannot be changed after material was placed`() {
		val f = EntriesFixture(api)
		val (a) = f.operators
		val inv = invoice(f, f.line(f.product(), 10, a to 10), f.line(f.product("Otra línea"), 5, a to 5))
		val id = inv.get("lines").list.single { it.get("quantity").asInt() == 10 }.get("assignments").get(0).get("id").asString()
		count(a, id, 10)
		assertEquals(200, place(a, id, f.locations[0] to 4).status)
		assertEquals("ASSIGNMENT_LOCKED", count(a, id, 8).code)
		assertEquals("ASSIGNMENT_LOCKED", adjust(f.admin, id).code)
	}
}
