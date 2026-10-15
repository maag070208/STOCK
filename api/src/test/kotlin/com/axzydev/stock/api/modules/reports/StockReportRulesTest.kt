package com.axzydev.stock.api.modules.reports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// F6-01 · unit: the total of the current stock report is the sum of the rows it lists, and the aisle and rack groups add
// up to their warehouse (reports.md section 3).
class StockReportRulesTest {

	private fun row(warehouse: String, aisle: String, rack: String, quantity: String): StockReportRow =
		StockReportRow(
			warehouse = ReportRef(UUID.randomUUID(), warehouse, "Almacén $warehouse"),
			aisle = aisle,
			rack = rack,
			location = ReportRef(UUID.randomUUID(), "$aisle$rack" + "A", null),
			product = ReportRef(UUID.randomUUID(), "M1", "Tornillo"),
			unit = ReportUnit("PIECE", "Pieza", 0),
			quantity = BigDecimal(quantity),
			minimum = BigDecimal.ZERO,
		)

	@Test
	fun `the total is the sum of the rows it lists`() {
		val rows = listOf(
			row("CENTRAL", "K", "05", "100"),
			row("CENTRAL", "K", "06", "50"),
			row("NORTE", "L", "01", "25"),
		)
		val totals = StockReportRules.totals(rows)
		assertEquals(0, BigDecimal(175).compareTo(totals.quantity))
		assertEquals(listOf("CENTRAL", "NORTE"), totals.warehouses.map { it.key })
		assertEquals(0, BigDecimal(150).compareTo(totals.warehouses.first { it.key == "CENTRAL" }.quantity))
		assertEquals(2L, totals.warehouses.first { it.key == "CENTRAL" }.rows)
		// The warehouse shows its name, and the group its aisle and rack together.
		assertEquals("Almacén CENTRAL", totals.warehouses.first { it.key == "CENTRAL" }.label)
		assertEquals(listOf("K05", "K06", "L01"), totals.groups.map { it.key })
	}

	@Test
	fun `the aisle and rack groups of a warehouse add up to it`() {
		val rows = listOf(
			row("CENTRAL", "K", "05", "100"),
			row("CENTRAL", "K", "05", "20"),
			row("CENTRAL", "K", "06", "50"),
			row("CENTRAL", "L", "01", "30"),
		)
		val totals = StockReportRules.totals(rows)
		assertEquals(listOf("K05", "K06", "L01"), totals.groups.map { it.key })
		// The report is one warehouse, so its groups add up to it, and the warehouse to the whole report.
		val warehouse = totals.warehouses.single()
		assertEquals(0, warehouse.quantity.compareTo(totals.groups.fold(BigDecimal.ZERO) { sum, group -> sum.add(group.quantity) }))
		assertEquals(warehouse.rows, totals.groups.sumOf { it.rows })
		assertEquals(0, totals.quantity.compareTo(warehouse.quantity))
	}

	@Test
	fun `a report with no rows has no totals`() {
		val totals = StockReportRules.totals(emptyList())
		assertEquals(0, BigDecimal.ZERO.compareTo(totals.quantity))
		assertEquals(emptyList<ReportTotal>(), totals.warehouses)
		assertEquals(emptyList<ReportTotal>(), totals.groups)
	}
}
