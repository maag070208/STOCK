package com.axzydev.stock.api.modules.reports

import java.math.BigDecimal

// The totals of the orders report (reports.md section 3): what was asked, what was picked and what moved between orders,
// with the picked quantity of each status. The parts add up to the whole, as in every report.
data class OrderReportTotals(
	val asked: BigDecimal,
	val picked: BigDecimal,
	val movedOut: BigDecimal,
	val movedIn: BigDecimal,
	val byStatus: List<ReportTotal>,
)

object OrderReportRules {

	fun totals(rows: List<OrderReportRow>): OrderReportTotals =
		OrderReportTotals(
			asked = sum(rows) { it.asked },
			picked = sum(rows) { it.picked },
			movedOut = sum(rows) { it.movedOut },
			movedIn = sum(rows) { it.movedIn },
			byStatus = byStatus(rows) { it.picked },
		)

	private fun byStatus(rows: List<OrderReportRow>, quantity: (OrderReportRow) -> BigDecimal): List<ReportTotal> =
		rows.groupBy { it.status.orEmpty() }
			.map { (status, ofStatus) -> ReportTotal(status, ofStatus.first().statusLabel ?: status, ofStatus.fold(BigDecimal.ZERO) { sum, row -> sum.add(quantity(row)) }, ofStatus.size.toLong()) }
			.sortedBy { it.key }

	private fun sum(rows: List<OrderReportRow>, of: (OrderReportRow) -> BigDecimal): BigDecimal =
		rows.fold(BigDecimal.ZERO) { total, row -> total.add(of(row)) }
}

// The totals of the entries report (reports.md section 3): what was expected, what was counted, the difference the
// office settled and what was placed, with the counted quantity of each status.
data class InvoiceReportTotals(
	val expected: BigDecimal,
	val counted: BigDecimal,
	val difference: BigDecimal,
	val placed: BigDecimal,
	val byStatus: List<ReportTotal>,
)

object InvoiceReportRules {

	fun totals(rows: List<InvoiceReportRow>): InvoiceReportTotals =
		InvoiceReportTotals(
			expected = sum(rows) { it.expected },
			counted = sum(rows) { it.counted },
			difference = sum(rows) { it.difference },
			placed = sum(rows) { it.placed },
			byStatus = rows.groupBy { it.status.orEmpty() }
				.map { (status, ofStatus) -> ReportTotal(status, ofStatus.first().statusLabel ?: status, sum(ofStatus) { it.counted }, ofStatus.size.toLong()) }
				.sortedBy { it.key },
		)

	private fun sum(rows: List<InvoiceReportRow>, of: (InvoiceReportRow) -> BigDecimal): BigDecimal =
		rows.fold(BigDecimal.ZERO) { total, row -> total.add(of(row)) }
}
