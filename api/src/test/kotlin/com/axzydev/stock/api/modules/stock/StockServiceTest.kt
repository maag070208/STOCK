package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// F4-01 and F4-02 · e2e: movements only grow, and each one moves its balance in the same transaction, under a lock.
// Run against real PostgreSQL. The concurrency cases run two transactions at once (testing.md rule 2).
class StockServiceTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var jdbc: JdbcTemplate

	private val actor = Actor(null, "test")

	// A warehouse with one rack, and a product: what a movement needs.
	private fun place(): Triple<String, String, String> {
		val warehouse = api.createWarehouse()
		val location = api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to "A", "rack" to "01", "levels" to 1))
			.json.get("locations").get(0).get("id").asString()
		val product = api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to "Tornillo", "unit" to "PIECE")).text("id")
		return Triple(warehouse, location, product)
	}

	private fun command(type: String, location: String, product: String, quantity: Int) =
		MovementCommand(type, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())

	private fun balance(location: String, product: String): BigDecimal =
		jdbc.queryForObject("select quantity from stock_balances where location_id = ?::uuid and product_id = ?::uuid", BigDecimal::class.java, location, product)!!

	@Test
	fun `a receipt writes its movement and raises the balance, and the movement is read but never changed`() {
		val (warehouse, location, product) = place()
		val movement = stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 500))).single()
		assertEquals(0, BigDecimal("500").compareTo(balance(location, product)))

		val read = api.get("/stock/movements/${movement.id}", api.adminToken())
		assertEquals(200, read.status, read.body)
		assertEquals("RECEIPT", read.json.get("type").get("code").asString())
		assertEquals("Entrada", read.json.get("type").get("label").asString())
		assertEquals(warehouse, read.text("warehouseId"))
		assertEquals(0, BigDecimal("500").compareTo(read.json.get("quantity").decimalValue()))
		assertEquals("test", read.json.get("source").get("type").asString())
		assertEquals(405, api.put("/stock/movements/${movement.id}", api.adminToken(), mapOf("quantity" to 1)).status)
		assertEquals(405, api.delete("/stock/movements/${movement.id}", api.adminToken()).status)
	}

	@Test
	fun `a movement out above the balance is refused, and nothing is written`() {
		val (_, location, product) = place()
		stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 10)))
		val refused = assertThrows<ApiException> { stock.apply(actor, listOf(command(StockService.OUT, location, product, 11))) }
		assertEquals(ErrorCode.STOCK_INSUFFICIENT, refused.code)
		assertEquals(0, BigDecimal.TEN.compareTo(balance(location, product)))
		assertEquals(1, jdbc.queryForObject("select count(*) from stock_movements where location_id = ?::uuid", Int::class.java, location))
	}

	@Test
	fun `the database refuses a negative balance and any change to a movement, even past the service`() {
		val (_, location, product) = place()
		stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 5)))
		assertThrows<Exception> { jdbc.update("update stock_balances set quantity = -1 where location_id = ?::uuid", location) }
		assertThrows<Exception> { jdbc.update("update stock_movements set quantity = 1 where location_id = ?::uuid", location) }
		assertThrows<Exception> { jdbc.update("delete from stock_movements where location_id = ?::uuid", location) }
	}

	@Test
	fun `an inactive location receives nothing, and the temporary location takes only the opening inventory`() {
		val (warehouse, location, product) = place()
		api.delete("/locations/$location", api.adminToken())
		val inactive = assertThrows<ValidationException> { stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 1))) }
		assertEquals(listOf("locationId" to ErrorCode.CATALOG_VALUE_INVALID), inactive.errors.map { it.field to it.code })

		val temporary = api.get("/warehouses/$warehouse", api.adminToken()).json.get("temporaryLocation").get("id").asString()
		assertThrows<ValidationException> { stock.apply(actor, listOf(command(StockService.RECEIPT, temporary, product, 1))) }
		stock.apply(actor, listOf(command(StockService.OPENING, temporary, product, 7)))
		assertEquals(0, BigDecimal("7").compareTo(balance(temporary, product)))
	}

	@Test
	fun `a quantity with more decimals than the product's unit allows is refused`() {
		val (_, location, product) = place()
		val refused = assertThrows<ValidationException> {
			stock.apply(actor, listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal("1.5"), null, "test", "x")))
		}
		assertEquals(listOf("quantity" to ErrorCode.QUANTITY_INVALID), refused.errors.map { it.field to it.code })
	}

	@Test
	fun `two receipts into the same location at the same time both count`() {
		val (_, location, product) = place()
		val results = parallel(2) { stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 100))) }
		assertTrue(results.all { it.isSuccess }, results.toString())
		assertEquals(0, BigDecimal("200").compareTo(balance(location, product)))
	}

	@Test
	fun `two movements out for the last units at the same time, exactly one succeeds and the balance never goes below zero`() {
		val (_, location, product) = place()
		stock.apply(actor, listOf(command(StockService.RECEIPT, location, product, 100)))
		val results = parallel(2) { stock.apply(actor, listOf(command(StockService.OUT, location, product, 60))) }
		assertEquals(1, results.count { it.isSuccess }, results.toString())
		assertEquals(ErrorCode.STOCK_INSUFFICIENT, (results.single { it.isFailure }.exceptionOrNull() as ApiException).code)
		assertEquals(0, BigDecimal("40").compareTo(balance(location, product)))
	}

	// Runs the same work in several threads, released together.
	private fun <T> parallel(threads: Int, work: () -> T): List<Result<T>> {
		val pool = Executors.newFixedThreadPool(threads)
		val start = CountDownLatch(1)
		val futures = (1..threads).map { pool.submit<Result<T>> { start.await(); runCatching(work) } }
		start.countDown()
		val results = futures.map { it.get(30, TimeUnit.SECONDS) }
		pool.shutdown()
		return results
	}
}
