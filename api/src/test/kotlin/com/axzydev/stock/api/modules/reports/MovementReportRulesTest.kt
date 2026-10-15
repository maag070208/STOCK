package com.axzydev.stock.api.modules.reports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// F6-03 · unit: the filters of the movements report — period, material, type, user and warehouse — combine with AND, and
// the totals of what came in and what went out add up (reports.md section 3).
class MovementReportRulesTest {

	private val warehouse = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
	private val otherWarehouse = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
	private val product = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
	private val otherProduct = UUID.fromString("00000000-0000-0000-0000-0000000000c2")
	private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000d1")
	private val beto = UUID.fromString("00000000-0000-0000-0000-0000000000d2")
	private val day = Instant.parse("2026-10-14T18:00:00Z")

	private fun movement(
		type: String,
		incoming: Boolean,
		quantity: String,
		warehouseId: UUID = warehouse,
		productId: UUID = product,
		user: UUID? = ana,
		at: Instant = day,
	) = MovementReportRow(
		seq = 1,
		occurredAt = at,
		type = type,
		typeLabel = if (incoming) "Entrada" else "Salida",
		warehouse = ReportRef(warehouseId, "CENTRAL", "Central"),
		location = ReportRef(UUID.randomUUID(), "K05A", null),
		product = ReportRef(productId, "M1", "Tornillo"),
		unit = ReportUnit("PIECE", "Pieza", 0),
		quantity = BigDecimal(quantity),
		incoming = incoming,
		user = user?.let { ReportRef(it, "ana", "Ana") },
	)

	@Test
	fun `every filter that is set must match, so they combine with AND`() {
		val row = movement("RECEIPT", incoming = true, quantity = "10")
		// Nothing set: every movement belongs to the report.
		assertTrue(MovementReportRules.matches(row, MovementFilters()))
		// Each filter on its own, matching and not matching.
		assertTrue(MovementReportRules.matches(row, MovementFilters(warehouseId = warehouse)))
		assertFalse(MovementReportRules.matches(row, MovementFilters(warehouseId = otherWarehouse)))
		assertTrue(MovementReportRules.matches(row, MovementFilters(productId = product)))
		assertFalse(MovementReportRules.matches(row, MovementFilters(productId = otherProduct)))
		assertTrue(MovementReportRules.matches(row, MovementFilters(type = "receipt")))
		assertFalse(MovementReportRules.matches(row, MovementFilters(type = "OUT")))
		assertTrue(MovementReportRules.matches(row, MovementFilters(userId = ana)))
		assertFalse(MovementReportRules.matches(row, MovementFilters(userId = beto)))
		// The period is half open: the first instant belongs to it, the one after the end does not.
		assertTrue(MovementReportRules.matches(row, MovementFilters(from = day, to = day.plusSeconds(1))))
		assertFalse(MovementReportRules.matches(row, MovementFilters(from = day.plusSeconds(1))))
		assertFalse(MovementReportRules.matches(row, MovementFilters(to = day)))
		// All of them together: one that does not match is enough to leave the movement out.
		val all = MovementFilters(warehouseId = warehouse, productId = product, type = "RECEIPT", userId = ana, from = day, to = day.plusSeconds(1))
		assertTrue(MovementReportRules.matches(row, all))
		assertFalse(MovementReportRules.matches(row, all.copy(userId = beto)))
		assertFalse(MovementReportRules.matches(row, all.copy(type = "OUT")))
		assertFalse(MovementReportRules.matches(row, all.copy(warehouseId = otherWarehouse)))
	}

	@Test
	fun `the totals are what came in, what went out and what each type moved`() {
		val totals = MovementReportRules.totals(
			listOf(
				movement("RECEIPT", incoming = true, quantity = "10"),
				movement("IN_RETURN", incoming = true, quantity = "2.5"),
				movement("OUT", incoming = false, quantity = "4"),
			),
		)
		assertEquals(0, BigDecimal("12.5").compareTo(totals.incoming))
		assertEquals(0, BigDecimal(4).compareTo(totals.outgoing))
		assertEquals(listOf("IN_RETURN", "OUT", "RECEIPT"), totals.byType.map { it.key })
		assertEquals(0, BigDecimal("2.5").compareTo(totals.byType.first { it.key == "IN_RETURN" }.quantity))
		assertEquals(1L, totals.byType.first { it.key == "OUT" }.rows)
		assertEquals("Salida", totals.byType.first { it.key == "OUT" }.label)
		// Everything the report lists is either in or out, so the parts add up to the whole.
		val whole = listOf("12.5", "4").fold(BigDecimal.ZERO) { sum, value -> sum.add(BigDecimal(value)) }
		assertEquals(0, whole.compareTo(totals.incoming.add(totals.outgoing)))
	}
}
