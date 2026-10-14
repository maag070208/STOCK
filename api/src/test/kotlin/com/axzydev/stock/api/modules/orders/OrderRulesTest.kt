package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.modules.orders.OrderRules.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// F5-06 and F5-10 to F5-12 · unit: the status machine reaches every allowed move and rejects every other one, and a
// reassignment never moves more than the line picked or than the target order still needs.
class OrderRulesTest {

	@Test
	fun `only the moves of the status machine are allowed`() {
		val allowed = setOf(
			Status.CREATED to Status.FULFILLING,
			Status.FULFILLING to Status.FULFILLED,
			Status.FULFILLED to Status.VALIDATED,
			Status.CREATED to Status.CANCELLED,
			Status.FULFILLING to Status.CANCELLED,
		)
		for (from in Status.entries) {
			for (to in Status.entries) {
				assertEquals(allowed.contains(from to to), OrderRules.canMove(from, to), "$from → $to")
			}
		}
	}

	@Test
	fun `a reassignment never moves more than the line picked or the target needs`() {
		// Moving picked material to another order: no more than the line picked (20), and no more than the target still
		// needs (15).
		assertNull(OrderRules.reassignableQuantity(BigDecimal(20), BigDecimal(15), BigDecimal(15)))
		assertEquals(
			ErrorCode.REASSIGN_EXCEEDS_PICKED,
			OrderRules.reassignableQuantity(BigDecimal(20), BigDecimal(15), BigDecimal(21)),
		)
		assertEquals(
			ErrorCode.REASSIGN_EXCEEDS_TARGET,
			OrderRules.reassignableQuantity(BigDecimal(20), BigDecimal(15), BigDecimal(16)),
		)
		// Returning to stock: only the picked quantity bounds it, because no target order is involved.
		assertNull(OrderRules.reassignableQuantity(BigDecimal(20), null, BigDecimal(20)))
		assertEquals(
			ErrorCode.REASSIGN_EXCEEDS_PICKED,
			OrderRules.reassignableQuantity(BigDecimal(20), null, BigDecimal(21)),
		)
	}
}
