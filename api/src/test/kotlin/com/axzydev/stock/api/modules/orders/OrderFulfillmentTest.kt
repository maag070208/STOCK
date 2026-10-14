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

// F5-04 to F5-06 · e2e: an order is assigned to an operator, each pick is an OUT movement, and the order moves
// CREATED → FULFILLING → FULFILLED. Run against real PostgreSQL.
class OrderFulfillmentTest : ApiTest() {

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

	private fun create(f: OrdersFixture, product: String, quantity: Int): JsonNode =
		f.create(f.body(f.line(product, quantity))).json

	private fun assign(f: OrdersFixture, order: JsonNode, operator: TestUser) =
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to operator.id))

	private fun pick(operator: TestUser, order: JsonNode, line: JsonNode, location: String, quantity: Int) =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/picks", operator.token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity), channel = "app",
		)

	@Test
	fun `the order goes to one active operator of its warehouse, and to no one else`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = create(f, product, 10)
		assertEquals(200, assign(f, order, f.operators[0]).status)
		val assigned = api.get("/orders/${order.get("id").asString()}", f.admin).json
		assertEquals(f.operators[0].id, assigned.get("operator").get("id").asString())
		// Someone who is not an operator of the warehouse cannot be assigned.
		val outsider = f.operator().also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to emptyList<String>())) }
		val refused = assign(f, order, outsider)
		assertEquals(400, refused.status, refused.body)
		assertEquals("CATALOG_VALUE_INVALID", refused.code)
	}

	@Test
	fun `a pick moves the material out and the order from CREATED to FULFILLING, and the last pick to FULFILLED`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val order = create(f, product, 30)
		val line = order.get("lines").get(0)
		assertEquals(200, assign(f, order, f.operators[0]).status)
		// The first pick of 10 leaves 20 to pick: the order is FULFILLING.
		val first = pick(f.operators[0], order, line, f.locations[0], 10)
		assertEquals(200, first.status, first.body)
		assertEquals("FULFILLING", first.json.get("status").get("code").asString())
		assertEquals(0, BigDecimal("90").compareTo(balance(f.locations[0], product)))
		// The last pick completes the line and the order.
		val last = pick(f.operators[0], order, line, f.locations[0], 20)
		assertEquals(200, last.status, last.body)
		assertEquals("FULFILLED", last.json.get("status").get("code").asString())
		assertEquals(0, BigDecimal("70").compareTo(balance(f.locations[0], product)))
		val picked = api.get("/orders/${order.get("id").asString()}", f.admin).json.get("lines").get(0)
		assertEquals(30, picked.get("pickedQuantity").asInt())
	}

	@Test
	fun `a pick above the location balance is refused, and one above the line is refused too`() {
		val f = OrdersFixture(api)
		val product = f.product()
		// 80 in one location and 20 in another: the warehouse holds 100, and the order may reserve it all.
		addStock(f.locations[0], product, 80)
		addStock(f.locations[1], product, 20)
		val order = create(f, product, 100)
		val line = order.get("lines").get(0)
		assertEquals(200, assign(f, order, f.operators[0]).status)
		// Above the location balance (locations[0] holds 80).
		val aboveBalance = pick(f.operators[0], order, line, f.locations[0], 90)
		assertEquals(409, aboveBalance.status, aboveBalance.body)
		assertEquals("STOCK_INSUFFICIENT", aboveBalance.code)
		// Above the line's remaining quantity (the line is 100).
		val aboveLine = pick(f.operators[0], order, line, f.locations[0], 101)
		assertEquals(409, aboveLine.status, aboveLine.body)
		assertEquals("PICK_EXCEEDS_LINE", aboveLine.code)
		// Nothing moved.
		assertEquals(0, BigDecimal("80").compareTo(balance(f.locations[0], product)))
	}

	@Test
	fun `a pick from another warehouse's location is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = create(f, product, 10)
		val line = order.get("lines").get(0)
		assertEquals(200, assign(f, order, f.operators[0]).status)
		val other = api.createWarehouse()
		val otherLocation = api.post("/locations/racks", f.admin, mapOf("warehouseId" to other, "aisle" to "A", "rack" to "01", "levels" to 1))
			.json.get("locations").get(0).get("id").asString()
		val refused = pick(f.operators[0], order, line, otherLocation, 10)
		assertEquals(409, refused.status, refused.body)
		assertEquals("LOCATION_NOT_IN_WAREHOUSE", refused.code)
	}

	@Test
	fun `an operator of another warehouse does not see the order`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = create(f, product, 10)
		val stranger = f.operator().also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		val read = api.get("/orders/${order.get("id").asString()}", stranger.token, channel = "app")
		assertEquals(404, read.status)
	}
}
