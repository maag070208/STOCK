package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.UUID

// F5-07 to F5-09 · e2e: a FULFILLED order is validated and locked, a shortage is accepted with a note, and an order
// with no picks is cancelled, releasing its reservation. Run against real PostgreSQL.
class OrderClosureTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	private fun create(f: OrdersFixture, product: String, quantity: Int): JsonNode =
		f.create(f.body(f.line(product, quantity))).json

	private fun assign(f: OrdersFixture, order: JsonNode, operator: TestUser) =
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to operator.id))

	private fun pick(operator: TestUser, order: JsonNode, line: JsonNode, location: String, quantity: Int) =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/picks", operator.token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity), channel = "app",
		)

	private fun validate(token: String, order: JsonNode, note: String? = null) =
		api.call("POST", "/orders/${order.get("id").asString()}/validation", token, if (note == null) emptyMap<String, Any>() else mapOf("note" to note))

	private fun accept(token: String, order: JsonNode, note: String) =
		api.post("/orders/${order.get("id").asString()}/accept-difference", token, mapOf("note" to note))

	private fun cancel(token: String, order: JsonNode) =
		api.post("/orders/${order.get("id").asString()}/cancellation", token)

	private fun status(f: OrdersFixture, order: JsonNode): String =
		api.get("/orders/${order.get("id").asString()}", f.admin).json.get("status").get("code").asString()

	// A manager of the fixture's warehouse, signed in on the web.
	private fun manager(f: OrdersFixture): TestUser =
		api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse))) }

	@Test
	fun `a fulfilled order is validated and locked, and no one without the permission validates it`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = create(f, product, 10)
		val line = order.get("lines").get(0)
		assign(f, order, f.operators[0])
		pick(f.operators[0], order, line, f.locations[0], 10)
		assertEquals("FULFILLED", status(f, order))
		// The operator cannot validate.
		assertEquals(403, validate(f.operators[0].token, order).status)
		// The administrator validates and the order locks.
		val validated = validate(f.admin, order)
		assertEquals(200, validated.status, validated.body)
		assertEquals("VALIDATED", validated.json.get("status").get("code").asString())
		assertEquals("VALIDATED", status(f, order))
		assertTrue(!api.get("/orders/${order.get("id").asString()}", f.admin).json.get("validatedAt").isNull)
		// Any change to a validated order is refused.
		val pickAgain = pick(f.operators[0], order, line, f.locations[0], 1)
		assertEquals(409, pickAgain.status, pickAgain.body)
		assertEquals("ORDER_LOCKED", pickAgain.code)
	}

	@Test
	fun `a shortage is accepted with a note by the manager, adjusting the line, and the office cannot accept it`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = create(f, product, 10)
		val line = order.get("lines").get(0)
		assign(f, order, f.operators[0])
		pick(f.operators[0], order, line, f.locations[0], 8)
		assertEquals("FULFILLING", status(f, order))
		// OFFICE cannot accept a difference.
		val office = api.newUser("OFFICE")
		assertEquals(403, accept(office.token, order, "El cliente acepta").status)
		// The manager accepts it with a note: the line becomes 8 and the order is FULFILLED.
		val m = manager(f)
		val accepted = accept(m.token, order, "El cliente acepta 8 de 10")
		assertEquals(200, accepted.status, accepted.body)
		assertEquals("FULFILLED", accepted.json.get("status").get("code").asString())
		assertEquals(8, accepted.json.get("lines").get(0).get("quantity").asInt())
	}

	@Test
	fun `an order with no picks is cancelled and releases its reservation, and one with picks is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 200)
		val order = create(f, product, 100)
		assertEquals(200, cancel(f.admin, order).status)
		assertEquals("CANCELLED", status(f, order))
		// The reservation is released: 100 are available again.
		val again = f.create(f.body(f.line(product, 100)))
		assertEquals(201, again.status, again.body)
		// An order with a pick cannot be cancelled.
		val other = create(f, product, 10)
		val line = other.get("lines").get(0)
		assign(f, other, f.operators[0])
		pick(f.operators[0], other, line, f.locations[0], 5)
		assertEquals("FULFILLING", status(f, other))
		val refused = cancel(f.admin, other)
		assertEquals(409, refused.status, refused.body)
		assertEquals("ORDER_HAS_PICKS", refused.code)
	}
}
