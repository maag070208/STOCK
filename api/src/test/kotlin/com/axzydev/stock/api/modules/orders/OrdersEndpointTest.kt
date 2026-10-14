package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
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
