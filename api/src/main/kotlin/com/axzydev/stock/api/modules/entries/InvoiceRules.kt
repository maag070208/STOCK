package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.errors.ErrorCode
import java.math.BigDecimal
import java.time.LocalDate

// The rules of material entry and receiving that need no database (material-entry.md, material-receiving.md).
object InvoiceRules {

	// Catalog codes. CLOSED means fully received and placed; CANCELLED means explicitly not received.
	enum class Status { OPEN, PENDING, IN_ASSIGNMENT, CLOSED, CANCELLED }

	private val NUMBER = Regex("^[A-Z0-9/-]+$")

	// Invoice number: 1 to 30 letters, digits, hyphens and slashes, uppercased first (validation.md, invoice fields).
	fun number(value: String): ErrorCode? =
		when {
			value.length > 30 -> ErrorCode.FIELD_TOO_LONG
			!NUMBER.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}


	// The expected quantities of a line add up to the line quantity (material-entry.md section 6).
	fun sharesAddUp(line: BigDecimal, shares: List<BigDecimal>): Boolean =
		shares.isNotEmpty() && shares.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(line) == 0

	// A counted quantity: zero or more (nothing may have arrived), at most 999,999, with the unit's decimals.
	fun counted(value: BigDecimal, decimals: Int): ErrorCode? =
		when {
			value.signum() < 0 || value > BigDecimal("999999") -> ErrorCode.QUANTITY_INVALID
			value.stripTrailingZeros().scale() > decimals -> ErrorCode.QUANTITY_INVALID
			else -> null
		}

	// counted − expected: zero is a match, anything else a discrepancy.
	fun difference(expected: BigDecimal, counted: BigDecimal): BigDecimal = counted - expected

	fun canMove(from: Status, to: Status): Boolean =
		from to to in setOf(
			Status.OPEN to Status.PENDING,
			Status.PENDING to Status.IN_ASSIGNMENT,
			Status.IN_ASSIGNMENT to Status.CLOSED,
			Status.OPEN to Status.CANCELLED,
			Status.PENDING to Status.CANCELLED,
		)

	// What is placed of an assignment never adds up to more than was counted.
	fun placementFits(counted: BigDecimal, placed: BigDecimal, adding: List<BigDecimal>): Boolean =
		adding.fold(placed, BigDecimal::add) <= counted
}
