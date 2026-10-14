package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.errors.ErrorCode
import java.math.BigDecimal
import java.time.LocalDate

// The rules of material entry and receiving that need no database (material-entry.md, material-receiving.md).
object InvoiceRules {

	// The INVOICE_STATUS values the code moves through (material-receiving.md section 8). They are catalog codes.
	enum class Status { OPEN, PENDING, IN_ASSIGNMENT, CLOSED }

	private val NUMBER = Regex("^[A-Z0-9/-]+$")

	// Invoice number: 1 to 30 letters, digits, hyphens and slashes, uppercased first (validation.md, invoice fields).
	fun number(value: String): ErrorCode? =
		when {
			value.length > 30 -> ErrorCode.FIELD_TOO_LONG
			!NUMBER.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	fun date(value: LocalDate, today: LocalDate): ErrorCode? = if (value.isAfter(today)) ErrorCode.DATE_IN_FUTURE else null

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

	// The adjustment (material-receiving.md section 5): the expected quantity becomes the counted one, and the line moves
	// by the same difference, so the line still equals the sum of its assignments. Gives (expected, line) after it.
	fun adjust(line: BigDecimal, expected: BigDecimal, counted: BigDecimal): Pair<BigDecimal, BigDecimal> =
		counted to line + difference(expected, counted)

	// Each status moves only to the next: OPEN, PENDING, IN_ASSIGNMENT, CLOSED.
	fun canMove(from: Status, to: Status): Boolean = to.ordinal == from.ordinal + 1

	// What is placed of an assignment never adds up to more than was counted.
	fun placementFits(counted: BigDecimal, placed: BigDecimal, adding: List<BigDecimal>): Boolean =
		adding.fold(placed, BigDecimal::add) <= counted
}
