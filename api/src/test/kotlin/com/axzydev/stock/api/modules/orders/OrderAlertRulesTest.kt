package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.modules.orders.OrderAlertRules.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// F5-13 · unit: when an open order raises an alert, and who is told (order-fulfillment.md section 7a).
class OrderAlertRulesTest {

	private val today = LocalDate.of(2026, 10, 14)

	@Test
	fun `an order raises its alert the day before, on the due date and every day after it`() {
		assertNull(OrderAlertRules.due(today.plusDays(2), today), "two days before is too early")
		assertEquals(Kind.BEFORE, OrderAlertRules.due(today.plusDays(1), today))
		assertEquals(Kind.DUE, OrderAlertRules.due(today, today))
		assertEquals(Kind.OVERDUE, OrderAlertRules.due(today.minusDays(1), today))
		assertEquals(Kind.OVERDUE, OrderAlertRules.due(today.minusDays(9), today))
	}

	@Test
	fun `the operator and the validators are told from the day before, and the creator from the due date on`() {
		val operator = UUID.randomUUID()
		val validator = UUID.randomUUID()
		val creator = UUID.randomUUID()
		val before = OrderAlertRules.recipients(Kind.BEFORE, operator, listOf(validator), creator)
		assertEquals(setOf(operator, validator), before)
		val due = OrderAlertRules.recipients(Kind.DUE, operator, listOf(validator), creator)
		assertEquals(setOf(operator, validator, creator), due)
		assertEquals(setOf(operator, validator, creator), OrderAlertRules.recipients(Kind.OVERDUE, operator, listOf(validator), creator))
	}

	@Test
	fun `an order with no operator or no creator tells whoever is left, and the same person once`() {
		val validator = UUID.randomUUID()
		assertEquals(setOf(validator), OrderAlertRules.recipients(Kind.BEFORE, null, listOf(validator), null))
		// The creator who also validates the order, or the operator who is the creator, is told once.
		assertEquals(setOf(validator), OrderAlertRules.recipients(Kind.DUE, validator, listOf(validator), validator))
		assertEquals(emptySet<UUID>(), OrderAlertRules.recipients(Kind.BEFORE, null, emptyList(), null))
	}
}
