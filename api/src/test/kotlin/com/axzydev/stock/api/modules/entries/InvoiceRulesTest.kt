package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.modules.entries.InvoiceRules.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// F4-07, F4-13 to F4-17 · unit: the invoice number and date, the shares of a line, counts and adjustments, the status
// machine, and how much a placement may take.
class InvoiceRulesTest {

	private fun d(value: Int) = BigDecimal(value)

	@Test
	fun `an invoice number is 1 to 30 letters, digits, hyphens or slashes`() {
		assertNull(InvoiceRules.number("FAC-2024/0012"))
		assertEquals(ErrorCode.FORMAT_INVALID, InvoiceRules.number("FAC 12"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, InvoiceRules.number("A".repeat(31)))
	}

	@Test
	fun `the shares of a line add up to the line quantity`() {
		assertTrue(InvoiceRules.sharesAddUp(d(100), listOf(d(60), d(40))))
		assertFalse(InvoiceRules.sharesAddUp(d(100), listOf(d(60), d(30))))
		assertFalse(InvoiceRules.sharesAddUp(d(100), emptyList()))
	}

	@Test
	fun `a count equal to the expected quantity is a match, any other is a discrepancy with its difference`() {
		assertEquals(BigDecimal.ZERO, InvoiceRules.difference(d(500), d(500)))
		assertEquals(d(-20), InvoiceRules.difference(d(100), d(80)))
		assertEquals(d(60), InvoiceRules.difference(d(120), d(180)))
	}

	@Test
	fun `an adjustment sets the expected quantity to the counted one and moves the line by the same difference`() {
		// Nails: expected 100, counted 80, the line was 100.
		assertEquals(d(80) to d(80), InvoiceRules.adjust(line = d(100), expected = d(100), counted = d(80)))
		// Wire: expected 120, counted 180.
		assertEquals(d(180) to d(180), InvoiceRules.adjust(line = d(120), expected = d(120), counted = d(180)))
		// A line of 100 split as 60 and 40; the 60 counted at 50: the line becomes 90.
		assertEquals(d(50) to d(90), InvoiceRules.adjust(line = d(100), expected = d(60), counted = d(50)))
		// Nothing arrived: an adjustment to zero is allowed.
		assertEquals(d(0) to d(40), InvoiceRules.adjust(line = d(100), expected = d(60), counted = d(0)))
	}

	@Test
	fun `each status moves only to the next one`() {
		val allowed = setOf(Status.OPEN to Status.PENDING, Status.PENDING to Status.IN_ASSIGNMENT, Status.IN_ASSIGNMENT to Status.CLOSED)
		for (from in Status.entries) for (to in Status.entries) {
			assertEquals(from to to in allowed, InvoiceRules.canMove(from, to), "$from to $to")
		}
	}

	@Test
	fun `placements may not add up to more than was counted`() {
		assertTrue(InvoiceRules.placementFits(counted = d(500), placed = d(0), adding = listOf(d(300), d(200))))
		assertTrue(InvoiceRules.placementFits(counted = d(500), placed = d(300), adding = listOf(d(200))))
		assertFalse(InvoiceRules.placementFits(counted = d(500), placed = d(300), adding = listOf(d(300))))
	}
}
