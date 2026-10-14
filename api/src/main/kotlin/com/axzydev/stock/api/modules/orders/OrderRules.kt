package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.errors.ErrorCode
import java.time.LocalDate

// The rules of an order that need no database (order-fulfillment.md sections 3, 4 and 6).
object OrderRules {

	// The ORDER_STATUS values the code moves through. They are catalog codes (order-fulfillment.md section 6).
	enum class Status { CREATED, FULFILLING, FULFILLED, VALIDATED, CANCELLED }

	private val NUMBER = Regex("^[A-Z0-9/-]+$")

	// Order number: 1 to 30 letters, digits, hyphens and slashes, uppercased first (order-fulfillment.md section 3).
	fun number(value: String): ErrorCode? =
		when {
			value.length > 30 -> ErrorCode.FIELD_TOO_LONG
			!NUMBER.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	// The due date is the day the order must leave, so it is never before today.
	fun dueDate(value: LocalDate, today: LocalDate): ErrorCode? = if (value.isBefore(today)) ErrorCode.DATE_IN_PAST else null

	// CREATED → FULFILLING → FULFILLED → VALIDATED, and CREATED or FULFILLING → CANCELLED (order-fulfillment.md section 6).
	fun canMove(from: Status, to: Status): Boolean =
		when (to) {
			Status.FULFILLING -> from == Status.CREATED
			Status.FULFILLED -> from == Status.FULFILLING
			Status.VALIDATED -> from == Status.FULFILLED
			Status.CANCELLED -> from == Status.CREATED || from == Status.FULFILLING
			Status.CREATED -> false
		}
}
