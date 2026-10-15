package com.axzydev.stock.api.modules.reports

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// The filters of the movements report (reports.md section 3). Every one that is set must match, so the period, the
// material, the type, the user and the warehouse combine with AND.
data class MovementFilters(
	val warehouseId: UUID? = null,
	val productId: UUID? = null,
	val type: String? = null,
	val userId: UUID? = null,
	val from: Instant? = null,
	val to: Instant? = null,
)

data class MovementTotals(val incoming: BigDecimal, val outgoing: BigDecimal, val byType: List<ReportTotal>)

object MovementReportRules {

	fun matches(movement: MovementReportRow, filters: MovementFilters): Boolean =
		(filters.warehouseId == null || movement.warehouse.id == filters.warehouseId) &&
			(filters.productId == null || movement.product.id == filters.productId) &&
			(filters.type == null || movement.type.equals(filters.type, ignoreCase = true)) &&
			(filters.userId == null || movement.user?.id == filters.userId) &&
			(filters.from == null || !movement.occurredAt.isBefore(filters.from)) &&
			(filters.to == null || movement.occurredAt.isBefore(filters.to))

	// What came in, what went out, and what each type moved. The parts add up to the whole, as in every report.
	fun totals(movements: List<MovementReportRow>): MovementTotals =
		MovementTotals(
			incoming = sum(movements.filter { it.incoming }),
			outgoing = sum(movements.filter { !it.incoming }),
			byType = movements.groupBy { it.type.orEmpty() }
				.map { (type, ofType) -> ReportTotal(type, ofType.first().typeLabel ?: type, sum(ofType), ofType.size.toLong()) }
				.sortedBy { it.key },
		)

	private fun sum(movements: List<MovementReportRow>): BigDecimal = movements.fold(BigDecimal.ZERO) { total, movement -> total.add(movement.quantity) }
}
