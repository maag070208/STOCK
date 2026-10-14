package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.UUID

// F5-10 to F5-12 · e2e: an open order changes operator, its picked quantities move to another order that needs them, and
// picked quantities return to stock. Run against real PostgreSQL.
class OrderReassignmentTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var jdbc: JdbcTemplate

	private val actor = Actor(null, "test")

	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	private fun balance(location: String, product: String): BigDecimal =
		jdbc.queryForObject(
			"select quantity from stock_balances where location_id = ?::uuid and product_id = ?::uuid",
			BigDecimal::class.java, location, product,
		)!!

	private fun inReturns(orderId: String): Int =
		jdbc.queryForObject(
			"""
			select count(*) from stock_movements m join catalog_item i on i.id = m.type_id
			where i.code = 'IN_RETURN' and m.source_type = 'order' and m.source_id = ?
			""".trimIndent(),
			Int::class.java, orderId,
		)!!

	private fun create(f: OrdersFixture, product: String, quantity: Int): JsonNode =
		f.create(f.body(f.line(product, quantity))).json

	private fun assign(f: OrdersFixture, order: JsonNode, operator: TestUser) =
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to operator.id))

	private fun pick(operator: TestUser, order: JsonNode, line: JsonNode, location: String, quantity: Int) =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/picks", operator.token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity), channel = "app",
		)

	private fun reassignOperator(token: String, order: JsonNode, operator: TestUser, note: String? = "Cambio de turno", channel: String = "web") =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/reassignments/operator", token,
			if (note == null) mapOf("operatorId" to operator.id) else mapOf("operatorId" to operator.id, "note" to note), channel = channel,
		)

	private fun transfer(token: String, order: JsonNode, line: JsonNode, target: JsonNode, quantity: Int, note: String? = "Pasa a otra orden") =
		api.post(
			"/orders/${order.get("id").asString()}/reassignments/transfer", token,
			if (note == null) mapOf("lineId" to line.get("id").asString(), "targetOrderId" to target.get("id").asString(), "quantity" to quantity)
			else mapOf("lineId" to line.get("id").asString(), "targetOrderId" to target.get("id").asString(), "quantity" to quantity, "note" to note),
		)

	private fun returnToStock(token: String, order: JsonNode, line: JsonNode, location: String, quantity: Int, note: String? = "Regresa a piso") =
		api.post(
			"/orders/${order.get("id").asString()}/reassignments/return", token,
			if (note == null) mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity)
			else mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity, "note" to note),
		)

	private fun view(token: String, order: JsonNode): JsonNode = api.get("/orders/${order.get("id").asString()}", token).json

	private fun picked(f: OrdersFixture, order: JsonNode): BigDecimal = view(f.admin, order).get("lines").get(0).get("pickedQuantity").decimalValue()

	private fun status(f: OrdersFixture, order: JsonNode): String =
		view(f.admin, order).get("status").get("code").asString()

	private fun audit(action: String, order: JsonNode): JsonNode =
		api.post(
			"/audit-log/query", api.adminToken(),
			mapOf("filters" to mapOf("action" to action, "targetType" to "order", "targetId" to order.get("id").asString())),
		).json

	// A manager of the fixture's warehouse, signed in on the web.
	private fun manager(f: OrdersFixture): TestUser =
		api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse))) }

	@Test
	fun `an open order changes operator with a note, keeping its picks and its balances`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val order = create(f, product, 30)
		val line = order.get("lines").get(0)
		assign(f, order, f.operators[0])
		pick(f.operators[0], order, line, f.locations[0], 20)
		assertEquals("FULFILLING", status(f, order))
		val m = manager(f)
		// The manager of the warehouse changes the operator: nothing moves, and the picks stay.
		val changed = reassignOperator(m.token, order, f.operators[1], "Cambio de turno")
		assertEquals(200, changed.status, changed.body)
		assertEquals(f.operators[1].id, view(f.admin, order).get("operator").get("id").asString())
		assertEquals(0, BigDecimal(20).compareTo(picked(f, order)))
		assertEquals(0, BigDecimal(80).compareTo(balance(f.locations[0], product)))
		assertEquals("FULFILLING", status(f, order))
		// The audit keeps the operator it came from, the one it went to and the note.
		val log = audit("ORDER_REASSIGNED", order)
		assertEquals(1, log.get("total").asInt(), log.toString())
		val entry = log.get("data").get(0).get("after")
		assertEquals(f.operators[1].id, entry.get("operatorId").asString())
		assertEquals("Cambio de turno", entry.get("note").asString())
		// A note is required, and the operator cannot reassign.
		assertEquals(400, reassignOperator(m.token, order, f.operators[0], null).status)
		val denied = reassignOperator(f.operators[0].token, order, f.operators[1], channel = "app")
		assertEquals(403, denied.status, denied.body)
		assertEquals("INSUFFICIENT_PERMISSIONS", denied.code)
		// An order that was never assigned is assigned, not reassigned.
		val fresh = create(f, product, 5)
		val unassigned = reassignOperator(m.token, fresh, f.operators[0])
		assertEquals(409, unassigned.status, unassigned.body)
		assertEquals("ORDER_STATUS_INVALID", unassigned.code)
		// A validated order is locked.
		val closed = create(f, product, 5)
		val closedLine = closed.get("lines").get(0)
		assign(f, closed, f.operators[0])
		pick(f.operators[0], closed, closedLine, f.locations[0], 5)
		assertEquals(200, api.post("/orders/${closed.get("id").asString()}/validation", f.admin, emptyMap<String, Any>()).status)
		val locked = reassignOperator(m.token, closed, f.operators[0])
		assertEquals(409, locked.status, locked.body)
		assertEquals("ORDER_LOCKED", locked.code)
	}

	@Test
	fun `picked quantities move to an order that needs them, and the source can then be cancelled`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val source = create(f, product, 30)
		val line = source.get("lines").get(0)
		assign(f, source, f.operators[0])
		pick(f.operators[0], source, line, f.locations[0], 20)
		val target = create(f, product, 20)
		val m = manager(f)
		// 20 picked units move to the order that needs them.
		val moved = transfer(m.token, source, line, target, 20, "El cliente cambió de orden")
		assertEquals(200, moved.status, moved.body)
		assertEquals(0, BigDecimal.ZERO.compareTo(picked(f, source)))
		assertEquals(0, BigDecimal(20).compareTo(picked(f, target)))
		// The goods were already out of the shelves: no balance changes.
		assertEquals(0, BigDecimal(80).compareTo(balance(f.locations[0], product)))
		// The target has every line picked, so it is ready to validate.
		assertEquals("FULFILLED", status(f, target))
		// The source has no picks left and can be cancelled.
		assertEquals(200, api.post("/orders/${source.get("id").asString()}/cancellation", m.token).status)
		assertEquals("CANCELLED", status(f, source))
		// The audit names the line, the target order, the quantity and the note.
		val log = audit("ORDER_PICKS_TRANSFERRED", source)
		assertEquals(1, log.get("total").asInt(), log.toString())
		val entry = log.get("data").get(0).get("after")
		assertEquals(target.get("id").asString(), entry.get("targetOrderId").asString())
		assertEquals(20, entry.get("quantity").asInt())
		assertEquals("El cliente cambió de orden", entry.get("note").asString())
	}

	@Test
	fun `a move above what the source picked or above what the target needs is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val source = create(f, product, 30)
		val line = source.get("lines").get(0)
		assign(f, source, f.operators[0])
		pick(f.operators[0], source, line, f.locations[0], 25)
		val target = create(f, product, 20)
		val m = manager(f)
		// More than the target still needs.
		val aboveTarget = transfer(m.token, source, line, target, 25)
		assertEquals(409, aboveTarget.status, aboveTarget.body)
		assertEquals("REASSIGN_EXCEEDS_TARGET", aboveTarget.code)
		// More than the source picked.
		val pickedSource = create(f, product, 30)
		val pickedLine = pickedSource.get("lines").get(0)
		assign(f, pickedSource, f.operators[0])
		pick(f.operators[0], pickedSource, pickedLine, f.locations[0], 5)
		val abovePicked = transfer(m.token, pickedSource, pickedLine, target, 10)
		assertEquals(409, abovePicked.status, abovePicked.body)
		assertEquals("REASSIGN_EXCEEDS_PICKED", abovePicked.code)
		// A note is required, and the operator cannot move anything.
		assertEquals(400, transfer(m.token, pickedSource, pickedLine, target, 5, null).status)
		val denied = api.call(
			"POST", "/orders/${pickedSource.get("id").asString()}/reassignments/transfer", f.operators[0].token,
			mapOf("lineId" to pickedLine.get("id").asString(), "targetOrderId" to target.get("id").asString(), "quantity" to 5, "note" to "Mueve"),
			channel = "app",
		)
		assertEquals(403, denied.status, denied.body)
		assertEquals("INSUFFICIENT_PERMISSIONS", denied.code)
	}

	@Test
	fun `a move to another warehouse, to another material or to a closed order is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val source = create(f, product, 30)
		val line = source.get("lines").get(0)
		assign(f, source, f.operators[0])
		pick(f.operators[0], source, line, f.locations[0], 20)
		val m = manager(f)
		// A target in another warehouse: the manager cannot even read it.
		val other = api.createWarehouse()
		val otherLocation = api.post("/locations/racks", f.admin, mapOf("warehouseId" to other, "aisle" to "A", "rack" to "01", "levels" to 1))
			.json.get("locations").get(0).get("id").asString()
		addStock(otherLocation, product, 10)
		val elsewhere = f.create(f.body(f.line(product, 10), changes = mapOf("warehouseId" to other))).json
		assertEquals(404, transfer(m.token, source, line, elsewhere, 10).status)
		// The administrator reads it, and the rule refuses it.
		val otherWarehouse = transfer(f.admin, source, line, elsewhere, 10)
		assertEquals(409, otherWarehouse.status, otherWarehouse.body)
		assertEquals("ORDER_WAREHOUSE_MISMATCH", otherWarehouse.code)
		// A target without that material.
		val otherProduct = f.product()
		addStock(f.locations[0], otherProduct, 10)
		val otherMaterial = create(f, otherProduct, 10)
		val notInOrder = transfer(f.admin, source, line, otherMaterial, 10)
		assertEquals(409, notInOrder.status, notInOrder.body)
		assertEquals("PRODUCT_NOT_IN_ORDER", notInOrder.code)
		// A closed target.
		val closed = create(f, product, 10)
		val closedLine = closed.get("lines").get(0)
		assign(f, closed, f.operators[0])
		pick(f.operators[0], closed, closedLine, f.locations[0], 10)
		assertEquals(200, api.post("/orders/${closed.get("id").asString()}/validation", f.admin, emptyMap<String, Any>()).status)
		val refused = transfer(f.admin, source, line, closed, 10)
		assertEquals(409, refused.status, refused.body)
		assertEquals("ORDER_LOCKED", refused.code)
	}

	@Test
	fun `picked quantities return to a location of the warehouse, and the order keeps its status`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		addStock(f.locations[1], product, 40)
		val order = create(f, product, 30)
		val line = order.get("lines").get(0)
		assign(f, order, f.operators[0])
		pick(f.operators[0], order, line, f.locations[0], 20)
		assertEquals(0, BigDecimal(80).compareTo(balance(f.locations[0], product)))
		val m = manager(f)
		val returned = returnToStock(m.token, order, line, f.locations[1], 20, "El material regresó a piso")
		assertEquals(200, returned.status, returned.body)
		// The chosen location holds the material again, and the line has nothing picked.
		assertEquals(0, BigDecimal(60).compareTo(balance(f.locations[1], product)))
		assertEquals(0, BigDecimal.ZERO.compareTo(picked(f, order)))
		assertEquals("FULFILLING", status(f, order))
		// The return is an IN_RETURN movement, and the audit keeps the location and the quantity.
		assertEquals(1, inReturns(order.get("id").asString()))
		val log = audit("ORDER_PICKS_RETURNED", order)
		assertEquals(1, log.get("total").asInt(), log.toString())
		val entry = log.get("data").get(0).get("after")
		assertEquals(f.locations[1], entry.get("locationId").asString())
		assertEquals(20, entry.get("quantity").asInt())
	}

	@Test
	fun `a return to another warehouse, above what was picked, or without a note is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val order = create(f, product, 30)
		val line = order.get("lines").get(0)
		assign(f, order, f.operators[0])
		pick(f.operators[0], order, line, f.locations[0], 5)
		val m = manager(f)
		// A location of another warehouse.
		val other = api.createWarehouse()
		val otherLocation = api.post("/locations/racks", f.admin, mapOf("warehouseId" to other, "aisle" to "A", "rack" to "01", "levels" to 1))
			.json.get("locations").get(0).get("id").asString()
		val elsewhere = returnToStock(m.token, order, line, otherLocation, 5)
		assertEquals(409, elsewhere.status, elsewhere.body)
		assertEquals("LOCATION_NOT_IN_WAREHOUSE", elsewhere.code)
		// More than the line picked.
		val above = returnToStock(m.token, order, line, f.locations[1], 6)
		assertEquals(409, above.status, above.body)
		assertEquals("REASSIGN_EXCEEDS_PICKED", above.code)
		// A note is required, and the operator cannot return anything.
		assertEquals(400, returnToStock(m.token, order, line, f.locations[1], 5, null).status)
		val denied = api.call(
			"POST", "/orders/${order.get("id").asString()}/reassignments/return", f.operators[0].token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to f.locations[1], "quantity" to 5, "note" to "Regresa"),
			channel = "app",
		)
		assertEquals(403, denied.status, denied.body)
		assertEquals("INSUFFICIENT_PERMISSIONS", denied.code)
	}
}
