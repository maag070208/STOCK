package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import java.math.BigDecimal

// The rules of a stock movement that need no database (validation.md, quantities; api.md section 4).
object StockRules {

	enum class Direction { IN, OUT }

	// 999,999 per field (validation.md, quantities, proposed).
	private val MAX = BigDecimal("999999")

	// Positive, at most the maximum, and with no more decimals than the unit's `decimal_places`.
	fun quantity(value: BigDecimal, decimals: Int): ErrorCode? =
		when {
			value.signum() <= 0 || value > MAX -> ErrorCode.QUANTITY_INVALID
			value.stripTrailingZeros().scale() > decimals -> ErrorCode.QUANTITY_INVALID
			else -> null
		}

	// The balance after a movement. A movement out above the balance is refused: a balance never goes below zero.
	fun apply(balance: BigDecimal, direction: Direction, quantity: BigDecimal): BigDecimal =
		when (direction) {
			Direction.IN -> balance + quantity
			// The message says how much is available.
			Direction.OUT -> if (quantity > balance) throw ApiException(ErrorCode.STOCK_INSUFFICIENT, balance.stripTrailingZeros().toPlainString()) else balance - quantity
		}

	// A movement type's direction is its catalog attribute, not its code (catalogs.md rule 5).
	fun direction(attributes: Map<String, Any?>): Direction? =
		when (attributes["direction"]) {
			"in" -> Direction.IN
			"out" -> Direction.OUT
			else -> null
		}
}
