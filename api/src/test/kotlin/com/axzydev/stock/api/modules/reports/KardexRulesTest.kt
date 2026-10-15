package com.axzydev.stock.api.modules.reports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// F6-02 · unit: the running balance after each movement is the previous balance plus or minus the movement, and the last
// running balance is the closing balance (reports.md section 3).
class KardexRulesTest {

	private val k05a = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
	private val k05b = UUID.fromString("00000000-0000-0000-0000-0000000000a2")

	private fun movement(seq: Long, location: UUID, quantity: String, incoming: Boolean) = MovementReportRow(
		seq = seq,
		occurredAt = Instant.parse("2026-10-14T10:00:00Z"),
		type = if (incoming) "RECEIPT" else "OUT",
		typeLabel = if (incoming) "Entrada" else "Salida",
		warehouse = ReportRef(UUID.randomUUID(), "CENTRAL", "Central"),
		location = ReportRef(location, if (location == k05a) "K05A" else "K05B", null),
		product = ReportRef(UUID.randomUUID(), "M1", "Tornillo"),
		unit = ReportUnit("PIECE", "Pieza", 0),
		quantity = BigDecimal(quantity),
		incoming = incoming,
	)

	@Test
	fun `each movement adds or subtracts from the running balance`() {
		val rows = KardexRules.running(
			listOf(
				movement(1, k05a, "10", incoming = true),
				movement(2, k05b, "5", incoming = true),
				movement(3, k05a, "3", incoming = false),
				movement(4, k05a, "2", incoming = true),
			),
		)
		// The material in the warehouse: 10, 15, 12, 14. Each location keeps its own balance: K05A 10, 7, 9 and K05B 5.
		assertEquals(listOf("10", "15", "12", "14"), rows.map { it.runningWarehouse?.toPlainString() })
		assertEquals(listOf("10", "5", "7", "9"), rows.map { it.runningLocation?.toPlainString() })
	}

	@Test
	fun `the last running balance is the closing balance`() {
		val rows = KardexRules.running(
			listOf(
				movement(1, k05a, "10", incoming = true),
				movement(2, k05a, "4", incoming = false),
				movement(3, k05a, "1", incoming = true),
			),
		)
		assertEquals(0, BigDecimal(7).compareTo(rows.last().runningWarehouse!!))
		assertEquals(0, BigDecimal(7).compareTo(rows.last().runningLocation!!))
		assertEquals(emptyList<MovementReportRow>(), KardexRules.running(emptyList()))
	}

	@Test
	fun `a kardex that starts later starts from what the material already had`() {
		// The material already had 10 in K05A and 5 in K05B when the listed period begins: 15 in the warehouse.
		val rows = KardexRules.running(
			listOf(movement(7, k05a, "3", incoming = false), movement(8, k05b, "2", incoming = true)),
			openingByLocation = mapOf(k05a to BigDecimal(10), k05b to BigDecimal(5)),
			openingWarehouse = BigDecimal(15),
		)
		assertEquals(listOf("12", "14"), rows.map { it.runningWarehouse?.toPlainString() })
		assertEquals(listOf("7", "7"), rows.map { it.runningLocation?.toPlainString() })
	}
}
