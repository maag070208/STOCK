package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.files.XlsxReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// F6-05 · unit: the export writes the rows of the screen for the same filters, and it is audited (reports.md section 4).
class ReportsExportServiceTest {

	private val reports = mockk<ReportsService>()
	private val audit = mockk<AuditService>(relaxed = true)
	private val exports = ReportsExportService(reports, audit)

	private val request = TableRequest(filters = mapOf("warehouseId" to UUID.randomUUID().toString()), limit = 200)
	private val filter = request.filters!!["warehouseId"] as String

	private fun user(vararg permissions: String) = CurrentUser(
		id = UUID.randomUUID(),
		username = "persona",
		roles = setOf("OFFICE"),
		warehouseIds = emptySet(),
		permissions = permissions.associateWith { Scope.ALL },
		mustChangePassword = false,
	)

	private fun row(location: String, quantity: String) = StockReportRow(
		warehouse = ReportRef(UUID.randomUUID(), "CENTRAL", "Central"),
		aisle = "K",
		rack = "05",
		location = ReportRef(UUID.randomUUID(), location, null),
		product = ReportRef(UUID.randomUUID(), "M1", "Tornillo"),
		unit = ReportUnit("PIECE", "Pieza", 0),
		quantity = BigDecimal(quantity),
		minimum = BigDecimal.ZERO,
	)

	@Test
	fun `the export writes the rows of the screen, with the same filters, and audits it`() {
		val reader = user("reports.view", "reports.export")
		val page = StockReportPage(
			data = listOf(row("K05A", "1500"), row("K05B", "2.5")),
			total = 2, page = 1, pageIndex = 0, totalPages = 1, totalCount = 2, limit = 200, hasPreviousPage = false, hasNextPage = false,
			quantity = BigDecimal("1502.5"), warehouseTotals = emptyList(), groupTotals = emptyList(),
		)
		every { reports.stock(reader, request, "es") } returns page

		val document = exports.document(reader, ReportKind.STOCK, request, "es")
		// The same call the screen makes, with the same filters, and one row per row.
		verify(exactly = 1) { reports.stock(reader, request, "es") }
		assertEquals(page.data.size, document.rows.size)
		assertEquals(listOf("CENTRAL", "K", "05", "K05A", "M1", "Tornillo", "Pieza", "1500"), document.rows[0])
		assertEquals("Existencias actuales", document.title)
		// The totals line of the document is the total of the report, not of one row.
		assertEquals("1502.5", document.totals.last())

		// The file carries the same rows: the sheet reads back with the totals of the screen.
		val file = exports.export(reader, ReportKind.STOCK, ReportFormat.XLSX, request, "es")
		verify(exactly = 2) { reports.stock(reader, request, "es") }
		assertTrue(file.fileName.startsWith("stock-") && file.fileName.endsWith(".xlsx"), file.fileName)
		assertTrue(file.contentType.contains("spreadsheetml"))
		val sheet = XlsxReader.read(file.bytes)
		assertEquals(document.columns, listOf("A", "B", "C", "D", "E", "F", "G", "H").map { sheet[0].cells[it] })
		assertEquals("1502.5", sheet[3].cells["H"])
		assertEquals("1500", sheet[1].cells["H"])

		// The export left its record: who, which report, which format, which filters, and how many rows.
		val after = slot<Any>()
		verify { audit.record(ofType<Actor>(), "REPORT_EXPORTED", "report", "STOCK", null, capture(after)) }
		@Suppress("UNCHECKED_CAST")
		val detail = after.captured as Map<String, Any?>
		assertEquals("XLSX", detail["format"])
		assertEquals(mapOf("warehouseId" to filter), detail["filters"])
		assertEquals(2, detail["rows"])

		// The PDF is the other shape of the same document.
		val pdf = exports.export(reader, ReportKind.STOCK, ReportFormat.PDF, request, "es")
		assertEquals("application/pdf", pdf.contentType)
		assertEquals("%PDF", String(pdf.bytes, 0, 4, Charsets.US_ASCII))
	}

	@Test
	fun `whoever reads a report does not necessarily export it`() {
		val reader = user("reports.view")
		every { reports.stock(reader, request, "es") } returns StockReportPage(
			emptyList(), 0, 1, 0, 0, 0, 200, false, false, BigDecimal.ZERO, emptyList(), emptyList(),
		)
		// Reading is allowed; exporting is its own permission.
		assertEquals(emptyList<List<String>>(), exports.document(reader, ReportKind.STOCK, request, "es").rows)
		assertThrows(ApiException::class.java) { exports.export(reader, ReportKind.STOCK, ReportFormat.XLSX, request, "es") }
		verify(exactly = 0) { audit.record(any(), "REPORT_EXPORTED", any(), any(), any(), any()) }
	}
}
