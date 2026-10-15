package com.axzydev.stock.api.modules.reports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// F6-04 · unit: the orders report sums what was asked, picked and moved per status and period, and the entries report
// sums what was expected, counted, settled and placed (reports.md section 3).
class SalesReportRulesTest {

	private val warehouse = ReportRef(UUID.randomUUID(), "CENTRAL", "Central")
	private val customer = ReportRef(UUID.randomUUID(), "CLI1", "Aceros SA")
	private val product = ReportRef(UUID.randomUUID(), "M1", "Tornillo")
	private val day = Instant.parse("2026-10-14T18:00:00Z")

	private fun orderRow(status: String, label: String, asked: String, picked: String, movedOut: String = "0", movedIn: String = "0") = OrderReportRow(
		order = ReportRef(UUID.randomUUID(), "ORD-1", null),
		warehouse = warehouse,
		customer = customer,
		status = status,
		statusLabel = label,
		dueDate = LocalDate.of(2026, 10, 20),
		product = product,
		asked = BigDecimal(asked),
		picked = BigDecimal(picked),
		movedOut = BigDecimal(movedOut),
		movedIn = BigDecimal(movedIn),
		createdAt = day,
		validatedAt = null,
	)

	private fun invoiceRow(status: String, label: String, expected: String, counted: String, difference: String, placed: String) = InvoiceReportRow(
		invoice = ReportRef(UUID.randomUUID(), "FAC-1", null),
		supplier = ReportRef(UUID.randomUUID(), "PROV1", "Aceros SA"),
		warehouse = warehouse,
		status = status,
		statusLabel = label,
		invoiceDate = LocalDate.of(2026, 10, 14),
		product = product,
		expected = BigDecimal(expected),
		counted = BigDecimal(counted),
		difference = BigDecimal(difference),
		placed = BigDecimal(placed),
	)

	@Test
	fun `the orders report sums what was asked, picked and moved, and the picked quantity of each status`() {
		val totals = OrderReportRules.totals(
			listOf(
				orderRow("FULFILLED", "Surtida", asked = "20", picked = "20", movedIn = "20"),
				orderRow("FULFILLING", "Surtiendo", asked = "30", picked = "0", movedOut = "20"),
				orderRow("FULFILLED", "Surtida", asked = "10", picked = "10"),
			),
		)
		assertEquals(0, BigDecimal(60).compareTo(totals.asked))
		assertEquals(0, BigDecimal(30).compareTo(totals.picked))
		assertEquals(0, BigDecimal(20).compareTo(totals.movedOut))
		assertEquals(0, BigDecimal(20).compareTo(totals.movedIn))
		// The status totals carry the picked quantity, with the label of the status in the language of the request.
		assertEquals(listOf("FULFILLED", "FULFILLING"), totals.byStatus.map { it.key })
		assertEquals(0, BigDecimal(30).compareTo(totals.byStatus.first { it.key == "FULFILLED" }.quantity))
		assertEquals("Surtida", totals.byStatus.first { it.key == "FULFILLED" }.label)
		assertEquals(2L, totals.byStatus.first { it.key == "FULFILLED" }.rows)
		assertEquals(0, BigDecimal.ZERO.compareTo(totals.byStatus.first { it.key == "FULFILLING" }.quantity))
	}

	@Test
	fun `the entries report sums what was expected, counted, settled and placed`() {
		val totals = InvoiceReportRules.totals(
			listOf(
				invoiceRow("CLOSED", "Cerrada", expected = "90", counted = "90", difference = "0", placed = "90"),
				invoiceRow("PENDING", "Pendiente", expected = "100", counted = "0", difference = "0", placed = "0"),
			),
		)
		assertEquals(0, BigDecimal(190).compareTo(totals.expected))
		assertEquals(0, BigDecimal(90).compareTo(totals.counted))
		assertEquals(0, BigDecimal.ZERO.compareTo(totals.difference))
		assertEquals(0, BigDecimal(90).compareTo(totals.placed))
		assertEquals(listOf("CLOSED", "PENDING"), totals.byStatus.map { it.key })
		assertEquals(0, BigDecimal(90).compareTo(totals.byStatus.first { it.key == "CLOSED" }.quantity))
		assertEquals("Cerrada", totals.byStatus.first { it.key == "CLOSED" }.label)
		// A report with nothing in it has no totals, and does not fail.
		assertEquals(0, BigDecimal.ZERO.compareTo(OrderReportRules.totals(emptyList()).asked))
		assertEquals(0, BigDecimal.ZERO.compareTo(InvoiceReportRules.totals(emptyList()).placed))
		assertEquals(emptyList<ReportTotal>(), OrderReportRules.totals(emptyList()).byStatus)
	}
}
