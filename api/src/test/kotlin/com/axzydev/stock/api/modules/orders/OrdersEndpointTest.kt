package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// F5-01 to F5-03 and F5-15 · e2e: an order is created against available stock, its lines reserve the quantity under a
// lock, a repeated number in the warehouse is refused, and creation is idempotent. Run against real PostgreSQL.
class OrdersEndpointTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	// Gives a material a balance in a location, as a receipt would.
	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	@Test
	fun `an order is created as CREATED and its lines are kept with their product`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val created = f.create(f.body(f.line(product, 10)))
		assertEquals(201, created.status, created.body)
		assertEquals("CREATED", created.json.get("status").get("code").asString())
		assertEquals("ORD", created.json.get("number").asString().substringBefore("-"))
		val line = created.json.get("lines").get(0)
		assertEquals(10, line.get("quantity").asInt())
		assertEquals(0, line.get("pickedQuantity").asInt())
	}

	@Test
	fun `a line above the available stock is refused with how much is available, and nothing is saved`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 140)
		val refused = f.create(f.body(f.line(product, 150)))
		assertEquals(409, refused.status, refused.body)
		assertEquals("STOCK_INSUFFICIENT", refused.code)
		assertTrue(refused.json.get("message").asString().contains("140"), refused.body)
		// Nothing was saved: asking again for what is available still finds 140.
		val ok = f.create(f.body(f.line(product, 140)))
		assertEquals(201, ok.status, ok.body)
	}

	@Test
	fun `the reservation falls by the reserved quantity, so two orders cannot take the same stock`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		assertEquals(201, f.create(f.body(f.line(product, 100))).status)
		// 100 are reserved: a second order for anything above 0 is refused.
		val refused = f.create(f.body(f.line(product, 1)))
		assertEquals(409, refused.status, refused.body)
		assertEquals("STOCK_INSUFFICIENT", refused.code)
		assertTrue(refused.json.get("message").asString().contains("0"), refused.body)
	}

	@Test
	fun `two orders created at the same time for the last unit, exactly one succeeds`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 1)
		val results = parallel(2) { f.create(f.body(f.line(product, 1))) }
		assertEquals(1, results.count { it.status == 201 }, results.joinToString { "${it.status}:${it.body}" })
		val refused = results.single { it.status == 409 }
		assertEquals("STOCK_INSUFFICIENT", refused.code)
	}

	@Test
	fun `a repeated number in the warehouse is refused, and the same number in another warehouse is accepted`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val number = f.number()
		assertEquals(201, f.create(f.body(f.line(product, 1), number = number)).status)
		val duplicate = f.create(f.body(f.line(product, 1), number = number))
		assertEquals(409, duplicate.status, duplicate.body)
		assertEquals("DUPLICATE_VALUE", duplicate.code)
		// The number is unique per warehouse, so another warehouse accepts it.
		val other = api.createWarehouse()
		val otherLocation = api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to other, "aisle" to "A", "rack" to "01", "levels" to 1))
			.json.get("locations").get(0).get("id").asString()
		addStock(otherLocation, product, 1)
		val body = f.body(f.line(product, 1), number = number, changes = mapOf("warehouseId" to other))
		assertEquals(201, f.create(body).status)
	}

	@Test
	fun `an inactive customer is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		api.delete("/customers/${f.customer}", f.admin)
		val refused = f.create(f.body(f.line(product, 1)))
		assertEquals(400, refused.status, refused.body)
		assertEquals("CATALOG_VALUE_INVALID", refused.code)
	}

	@Test
	fun `creation is idempotent, the same key and request answer the first response, and a different request reusing the key is refused`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val key = UUID.randomUUID().toString()
		val number = f.number()
		val first = f.create(f.body(f.line(product, 1), number = number), key = key)
		assertEquals(201, first.status, first.body)
		val second = f.create(f.body(f.line(product, 1), number = number), key = key)
		assertEquals(201, second.status)
		assertEquals(first.json.get("id").asString(), second.json.get("id").asString())
		// The same key with a different request is refused.
		val reused = f.create(f.body(f.line(product, 2), number = number), key = key)
		assertEquals(409, reused.status, reused.body)
		assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code)
	}

	@Test
	fun `the query filters by one status or by several, and by the assigned operator`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val created = f.create(f.body(f.line(product, 10))).json
		val fulfilling = f.create(f.body(f.line(product, 10))).json
		val fulfillingLine = fulfilling.get("lines").get(0)
		api.put("/orders/${fulfilling.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))
		api.call(
			"POST", "/orders/${fulfilling.get("id").asString()}/picks", f.operators[0].token,
			mapOf("lineId" to fulfillingLine.get("id").asString(), "locationId" to f.locations[0], "quantity" to 4), channel = "app",
		)
		val cancelled = f.create(f.body(f.line(product, 10))).json
		assertEquals(200, api.post("/orders/${cancelled.get("id").asString()}/cancellation", f.admin).status)

		val numbers = { status: Any -> query(f, mapOf("status" to status)).map { it.get("number").asString() }.toSet() }
		assertEquals(setOf(fulfilling.get("number").asString()), numbers("FULFILLING"))
		assertEquals(
			setOf(created.get("number").asString(), fulfilling.get("number").asString()),
			numbers(listOf("CREATED", "FULFILLING")),
		)
		assertEquals(emptySet<String>(), numbers("NOPE"))
		assertEquals(emptySet<String>(), numbers(listOf("CREATED", "NOPE")))
		val mine = query(f, mapOf("operatorId" to f.operators[0].id)).map { it.get("number").asString() }.toSet()
		assertEquals(setOf(fulfilling.get("number").asString()), mine)
	}

	// The rows of one warehouse, with these filters on top.
	private fun query(f: OrdersFixture, filters: Map<String, Any>): List<tools.jackson.databind.JsonNode> =
		api.post("/orders/query", f.admin, mapOf("filters" to (filters + mapOf("warehouseId" to f.warehouse)), "limit" to 200))
			.also { assertEquals(200, it.status, it.body) }.json.get("data").let { rows -> rows.values().toList() }

	@Test
	fun `whoever creates orders lists the warehouses a new order may leave from`() {
		val f = OrdersFixture(api)
		val office = api.newUser("OFFICE")
		val listed = api.get("/orders/warehouse-options", office.token)
		assertEquals(200, listed.status, listed.body)
		assertTrue(listed.json.list.any { it.get("id").asString() == f.warehouse }, listed.body)
		assertTrue(listed.json.list.all { it.get("code").asString().isNotBlank() })
		// Without orders.create there is nothing to fill the form with.
		assertEquals(403, api.get("/orders/warehouse-options", api.newUser("WAREHOUSE_MANAGER").token).status)
	}

	@Test
	fun `the availability of a material is its balance minus what open orders reserve`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 140)
		// 100 are reserved by an open order: 40 are available.
		assertEquals(201, f.create(f.body(f.line(product, 100))).status)
		val asked = api.post("/orders/availability", f.admin, mapOf("warehouseId" to f.warehouse, "productIds" to listOf(product)))
		assertEquals(200, asked.status, asked.body)
		val row = asked.json.get("items").get(0)
		assertEquals(product, row.get("productId").asString())
		assertEquals(0, BigDecimal(40).compareTo(row.get("available").decimalValue()))
		// Another warehouse, with no stock and no orders, has nothing available.
		val other = api.createWarehouse()
		val empty = api.post("/orders/availability", f.admin, mapOf("warehouseId" to other, "productIds" to listOf(product)))
		assertEquals(0, BigDecimal.ZERO.compareTo(empty.json.get("items").get(0).get("available").decimalValue()))
		// Someone who reads no order of that warehouse cannot ask, and the field is required.
		val stranger = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${stranger.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(other)))
		assertEquals(404, api.post("/orders/availability", stranger.token, mapOf("warehouseId" to f.warehouse, "productIds" to listOf(product))).status)
		assertEquals(400, api.post("/orders/availability", f.admin, mapOf("productIds" to listOf(product))).status)
	}

	// Runs the same work in several threads, released together.
	private fun <T> parallel(threads: Int, work: () -> T): List<T> {
		val pool = Executors.newFixedThreadPool(threads)
		val start = CountDownLatch(1)
		val futures = (1..threads).map { pool.submit<T> { start.await(); work() } }
		start.countDown()
		val results = futures.map { it.get(30, TimeUnit.SECONDS) }
		pool.shutdown()
		return results
	}
}
