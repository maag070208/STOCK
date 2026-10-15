package com.axzydev.stock.api.modules.reports

import java.math.BigDecimal

// The totals of the current stock report (reports.md section 3): by warehouse, by aisle and rack, and the grand total.
// All of them come from the same rows, so the parts always add up to the whole, and the screen and the file agree.
object StockReportRules {

	fun totals(rows: List<StockReportRow>): StockReportTotals =
		StockReportTotals(
			quantity = sum(rows),
			warehouses = grouped(rows) { row ->
				(row.warehouse.code ?: row.warehouse.id.toString()) to (row.warehouse.name ?: row.warehouse.code.orEmpty())
			},
			groups = grouped(rows) { row -> row.group to row.group },
		)

	private fun sum(rows: List<StockReportRow>): BigDecimal = rows.fold(BigDecimal.ZERO) { total, row -> total.add(row.quantity) }

	private fun grouped(rows: List<StockReportRow>, key: (StockReportRow) -> Pair<String, String>): List<ReportTotal> =
		rows.groupBy(key)
			.map { (group, ofGroup) -> ReportTotal(group.first, group.second, sum(ofGroup), ofGroup.size.toLong()) }
			.sortedBy { it.key }
}
