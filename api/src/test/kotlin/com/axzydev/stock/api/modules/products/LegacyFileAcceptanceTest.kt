package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

// F2 acceptance criteria 4 and 5, and F4 criteria 1 and 2, on the real legacy file. The file is never in the repository (F2-07),
// so this runs only when LEGACY_FILE names it: LEGACY_FILE=/path/to/file.xlsx ./gradlew test --tests '*LegacyFileAcceptanceTest*'
// It runs in the test database, so the real products are never written to a shared one.
@EnabledIfEnvironmentVariable(named = "LEGACY_FILE", matches = ".+")
class LegacyFileAcceptanceTest : ApiTest() {

	@Test
	fun `the legacy file gives 1,398 products and 2 rows without a code, its stock adds up to the control total, and second runs write nothing`() {
		val file = File(System.getenv("LEGACY_FILE")).readBytes()
		val report = api.upload("/products/import/dry-run", api.adminToken(), file)
		assertEquals(200, report.status, report.body)
		assertEquals(1398, report.json.get("accepted").asInt())
		val rejected = report.json.get("rejected").list
		assertEquals(2, rejected.size)
		assertTrue(rejected.all { it.get("field").asString() == "code" && it.get("code").asString() == "FIELD_REQUIRED" })
		assertEquals(10_914_130L, report.json.get("controlTotal").asLong())
		assertTrue(report.json.get("controlMatches").asBoolean())

		val checksum = report.json.get("checksum").asString()
		val first = api.upload("/products/import", api.adminToken(), file, mapOf("checksum" to checksum))
		assertEquals(1398, first.json.get("created").asInt(), first.body)
		val second = api.upload("/products/import", api.adminToken(), file, mapOf("checksum" to checksum))
		assertEquals(0, second.json.get("created").asInt())
		assertEquals(1398, second.json.get("existingProducts").asInt())

		// F4 criteria 1 and 2: the opening inventory of the same file, into TEMPORARY of a warehouse named CENTRAL.
		val central = api.post("/warehouses", api.adminToken(), mapOf("code" to "CENTRAL", "name" to "Almacén central"))
		val warehouse = if (central.status == 201) central.text("id") else api.post("/warehouses/query", api.adminToken(), mapOf("filters" to mapOf("code" to "CENTRAL"))).json.get("data").get(0).get("id").asString()
		val opening = api.upload("/stock/opening/dry-run", api.adminToken(), file, mapOf("warehouseId" to warehouse))
		assertEquals(200, opening.status, opening.body)
		assertEquals(1398, opening.json.get("products").asInt())
		assertEquals(0, java.math.BigDecimal(10_914_130).compareTo(opening.json.get("total").decimalValue()))
		assertTrue(opening.json.get("controlMatches").asBoolean())
		val loaded = api.upload("/stock/opening", api.adminToken(), file, mapOf("warehouseId" to warehouse, "checksum" to checksum))
		assertEquals(200, loaded.status, loaded.body)
		assertEquals(opening.json.get("withStock").asInt(), loaded.json.get("movements").asInt())
		val again = api.upload("/stock/opening", api.adminToken(), file, mapOf("warehouseId" to warehouse, "checksum" to checksum))
		assertEquals(0, again.json.get("movements").asInt())

		// F6 criterion 1: the current stock report totals what the opening inventory loaded.
		val stockReport = api.post("/reports/stock", api.adminToken(), mapOf("filters" to mapOf("warehouseId" to warehouse), "limit" to 200))
		assertEquals(200, stockReport.status, stockReport.body)
		assertEquals(0, java.math.BigDecimal(10_914_130).compareTo(stockReport.json.get("quantity").decimalValue()), stockReport.body)
	}
}
