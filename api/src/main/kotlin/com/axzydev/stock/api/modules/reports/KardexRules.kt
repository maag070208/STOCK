package com.axzydev.stock.api.modules.reports

import java.math.BigDecimal
import java.util.UUID

// The running balances of a kardex (reports.md section 3): each movement adds or subtracts, following the order it is
// listed in. Two of them are kept: the one of the location the movement touched, and the one of the material in the
// warehouse. The openings are what the material already had before the first listed movement — zero when the kardex
// starts at the beginning — so a kardex over a period still ends at the balance of that period.
object KardexRules {

	fun running(
		movements: List<MovementReportRow>,
		openingByLocation: Map<UUID, BigDecimal> = emptyMap(),
		openingWarehouse: BigDecimal = BigDecimal.ZERO,
	): List<MovementReportRow> {
		val locationBalances = openingByLocation.toMutableMap()
		var warehouse = openingWarehouse
		return movements.map { movement ->
			val change = if (movement.incoming) movement.quantity else movement.quantity.negate()
			val location = (locationBalances[movement.location.id] ?: BigDecimal.ZERO).add(change)
			locationBalances[movement.location.id] = location
			warehouse = warehouse.add(change)
			movement.copy(runningLocation = location, runningWarehouse = warehouse)
		}
	}
}
