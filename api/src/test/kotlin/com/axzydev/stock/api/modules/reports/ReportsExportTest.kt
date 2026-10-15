package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.files.XlsxReader
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

// F6-05 · e2e: the export of a report — the XLSX carries the rows and the totals of the screen, the PDF is a PDF, and
// every export leaves its record in the audit log. Run against real PostgreSQL.
class ReportsExportTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	private fun rack(warehouse: String, aisle: String, rack: String, levels: Int): List<String> =
		api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to aisle, "rack" to rack, "levels" to levels))
			.json.get("locations").list.map { it.get("id").asString() }

	private fun product(name: String): String =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	private fun receive(location: String, product: String, quantity: Int) = stock.apply(
		actor,
		listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), null, "test", UUID.randomUUID().toString())),
	)

	private fun screen(filters: Map<String, Any>, token: String = api.adminToken()) =
		api.post("/reports/stock", token, mapOf("filters" to filters, "limit" to 200)).json

	private fun export(report: String, format: String, filters: Map<String, Any>, token: String = api.adminToken(), channel: String = "web") =
		api.downloadPost("/reports/$report/export", token, mapOf("format" to format, "filters" to filters), channel)

	@Test
	fun `the xlsx carries the rows and the totals of the screen, and the export is audited`() {
		val warehouse = api.createWarehouse()
		val (k05a, k05b) = rack(warehouse, "K", "05", 2)
		val screws = product("Tornillo")
		val nails = product("Clavo")
		receive(k05a, screws, 1500)
		receive(k05b, nails, 25)
		val filters = mapOf("warehouseId" to warehouse)

		val expected = screen(filters)
		assertEquals(0, BigDecimal("1525").compareTo(expected.get("quantity").decimalValue()))

		val file = export("stock", "XLSX", filters)
		assertEquals(200, file.status, file.text)
		assertTrue(file.contentType.orEmpty().contains("spreadsheetml"), file.contentType)
		assertTrue(file.contentDisposition.orEmpty().contains(".xlsx"), file.contentDisposition)
		val sheet = XlsxReader.read(file.bytes)
		// The headings are the document's labels, the rows are the rows of the screen, and the last line is its total.
		assertEquals("Cantidad", sheet[0].cells["H"])
		assertEquals("Material", sheet[0].cells["F"])
		assertEquals(4, sheet.size)
		assertEquals("1500", sheet[1].cells["H"])
		assertEquals("25", sheet[2].cells["H"])
		assertEquals("Total", sheet[3].cells["A"])
		assertEquals(expected.get("quantity").decimalValue().stripTrailingZeros().toPlainString(), sheet[3].cells["H"])

		// The record: who exported what, in which format, and how many rows.
		val log = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "REPORT_EXPORTED", "targetType" to "report", "targetId" to "STOCK")))
		assertEquals(1, log.json.get("total").asInt(), log.body)
		assertTrue(log.body.contains("XLSX"), log.body)

		// The other shape, and the two the contract names are the only ones.
		val pdf = export("stock", "PDF", filters)
		assertEquals(200, pdf.status)
		assertEquals("application/pdf", pdf.contentType)
		assertEquals("%PDF", String(pdf.bytes, 0, 4, Charsets.US_ASCII))
		assertEquals(404, export("nope", "XLSX", filters).status)
		assertEquals(400, export("stock", "CSV", filters).status)

		// Exporting is its own permission: a reader of reports without it is refused, and so is an operator.
		val manager = api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(warehouse))) }
		assertEquals(200, export("stock", "XLSX", filters, manager.token).status)
		val reader = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		assertEquals(403, export("stock", "XLSX", filters, reader.token, "app").status)
	}

	@Test
	fun `every report exports its own file`() {
		val warehouse = api.createWarehouse()
		val (k05a, _) = rack(warehouse, "K", "05", 2)
		val screws = product("Tornillo")
		receive(k05a, screws, 40)

		// The kardex, the movements, the orders and the entries leave a file each, with their own columns.
		val sheets = mapOf(
			"kardex" to mapOf("productId" to screws, "warehouseId" to warehouse),
			"movements" to mapOf("warehouseId" to warehouse),
			"orders" to mapOf("warehouseId" to warehouse),
			"invoices" to mapOf("warehouseId" to warehouse),
		)
		sheets.forEach { (report, filters) ->
			val file = export(report, "XLSX", filters)
			assertEquals(200, file.status, "$report: ${file.text}")
			val sheet = XlsxReader.read(file.bytes)
			assertTrue(sheet.isNotEmpty(), "$report has no sheet")
			val headers = sheet[0].cells.values.filter { it.isNotBlank() }
			assertTrue(headers.size >= 8, "$report: $headers")
		}
		// The kardex file names its material and its warehouse.
		val kardex = XlsxReader.read(export("kardex", "XLSX", mapOf("productId" to screws, "warehouseId" to warehouse)).bytes)
		assertEquals("40", kardex[1].cells["E"])
	}
}
