package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.Xlsx
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

// F4-03 and F4-04 · e2e: the opening inventory. The dry run reports the products and the total and writes nothing;
// the confirmation writes OPENING movements into the warehouse's TEMPORARY location in one transaction, only when the
// total matches the control total, and a second run writes nothing.
class OpeningInventoryEndpointTest : ApiTest() {

	@Autowired
	lateinit var jdbc: JdbcTemplate

	// A file with this test's own products, already imported as products (F2), and its warehouse.
	private fun setup(stocks: List<Int> = listOf(300, 0, 200), control: Long? = null): Triple<ByteArray, String, List<String>> {
		val prefix = "T" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
		val firstId = Random.nextInt(2_000_000, 2_000_000_000)
		val codes = stocks.indices.map { "$prefix$it" }
		val rows = stocks.mapIndexed { i, stock -> Xlsx.product(firstId + i, codes[i], "Material $i", stock = stock) }
		val file = Xlsx.build(listOf(Xlsx.HEADER) + rows + Xlsx.control(control ?: stocks.sum().toLong()))
		val imported = api.upload("/products/import/dry-run", api.adminToken(), file)
		api.upload("/products/import", api.adminToken(), file, mapOf("checksum" to imported.text("checksum")))
		return Triple(file, api.createWarehouse(uniqueCode("CENTRAL")), codes)
	}

	private fun dryRun(file: ByteArray, warehouse: String, token: String = api.adminToken()) =
		api.upload("/stock/opening/dry-run", token, file, mapOf("warehouseId" to warehouse))

	private fun confirm(file: ByteArray, warehouse: String, checksum: String) =
		api.upload("/stock/opening", api.adminToken(), file, mapOf("warehouseId" to warehouse, "checksum" to checksum))

	private fun temporaryBalances(warehouse: String): Map<String, BigDecimal> {
		val temporary = api.get("/warehouses/$warehouse", api.adminToken()).json.get("temporaryLocation").get("id").asString()
		return jdbc.queryForList(
			"select p.code, b.quantity from stock_balances b join products p on p.id = b.product_id where b.location_id = ?::uuid", temporary,
		).associate { it["code"] as String to it["quantity"] as BigDecimal }
	}

	@Test
	fun `the dry run reports the products, the units and the control, and writes nothing`() {
		val (file, warehouse, _) = setup()
		val report = dryRun(file, warehouse)
		assertEquals(200, report.status, report.body)
		assertEquals(3, report.json.get("products").asInt())
		assertEquals(2, report.json.get("withStock").asInt())
		assertEquals(0, BigDecimal(500).compareTo(report.json.get("total").decimalValue()))
		assertEquals(500, report.json.get("controlTotal").asInt())
		assertTrue(report.json.get("controlMatches").asBoolean())
		assertEquals(0, report.json.get("missing").size())
		assertEquals(emptyMap<String, BigDecimal>(), temporaryBalances(warehouse))
	}

	@Test
	fun `the confirmation writes the opening movements into TEMPORARY, and a second run writes nothing`() {
		val (file, warehouse, codes) = setup()
		val checksum = dryRun(file, warehouse).text("checksum")
		val first = confirm(file, warehouse, checksum)
		assertEquals(200, first.status, first.body)
		assertEquals(2, first.json.get("movements").asInt())
		val balances = temporaryBalances(warehouse)
		assertEquals(0, BigDecimal(300).compareTo(balances.getValue(codes[0])))
		assertEquals(0, BigDecimal(200).compareTo(balances.getValue(codes[2])))
		assertEquals(setOf(codes[0], codes[2]), balances.keys)
		assertEquals(2, jdbc.queryForObject("select count(*) from stock_movements where source_type = 'opening_inventory' and warehouse_id = ?::uuid", Int::class.java, warehouse))

		val again = confirm(file, warehouse, checksum)
		assertEquals(200, again.status, again.body)
		assertEquals(0, again.json.get("movements").asInt())
		assertTrue(again.json.get("alreadyLoaded").asBoolean())
		assertEquals(0, BigDecimal(300).compareTo(temporaryBalances(warehouse).getValue(codes[0])))
	}

	@Test
	fun `a total that does not match the control total is refused in full`() {
		val (file, warehouse, _) = setup(listOf(300, 200), control = 499)
		val report = dryRun(file, warehouse)
		assertEquals(false, report.json.get("controlMatches").asBoolean())
		assertEquals("IMPORT_CONTROL_MISMATCH", confirm(file, warehouse, report.text("checksum")).code)
		assertEquals(emptyMap<String, BigDecimal>(), temporaryBalances(warehouse))
	}

	@Test
	fun `a product of the file that does not exist is reported, and the load is refused`() {
		val (_, warehouse, _) = setup()
		val unknown = Xlsx.build(listOf(Xlsx.HEADER, Xlsx.product(Random.nextInt(2_000_000, 2_000_000_000), "NOEXISTE${Random.nextInt(1000, 9999)}", "Nada", stock = 5)) + Xlsx.control(5))
		val report = dryRun(unknown, warehouse)
		assertEquals(1, report.json.get("missing").size())
		assertEquals(false, report.json.get("controlMatches").asBoolean())
		assertEquals("IMPORT_CONTROL_MISMATCH", confirm(unknown, warehouse, report.text("checksum")).code)
	}

	@Test
	fun `only stock opening loads it, and a changed file is refused`() {
		val (file, warehouse, _) = setup()
		val office = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", dryRun(file, warehouse, office.token).code)
		val checksum = dryRun(file, warehouse).text("checksum")
		val (other, _, _) = setup()
		assertEquals("IMPORT_FILE_CHANGED", confirm(other, warehouse, checksum).code)
		assertEquals(listOf("warehouseId"), api.upload("/stock/opening/dry-run", api.adminToken(), file).json.get("details").list.map { it.get("field").asString() })
	}
}
