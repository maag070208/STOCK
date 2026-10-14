package com.axzydev.stock.ui

import com.axzydev.stock.ui.format.formatQuantity
import kotlin.test.Test
import kotlin.test.assertEquals

// F3-13 · unit: a quantity is shown with its unit's decimals and its thousands grouped, as the web shows it.
class QuantityFormatTest {

	@Test
	fun theUnitsDecimalsAndTheThousands() {
		assertEquals("1,234.50", formatQuantity("1234.500000", 2))
		assertEquals("12", formatQuantity("12.000000", 0))
		assertEquals("1,000,000", formatQuantity("1000000", 0))
		assertEquals("0.125", formatQuantity("0.125000", 3))
		assertEquals("7", formatQuantity("7", null))
		assertEquals("0", formatQuantity("0.000000", 0))
		assertEquals("-3.5", formatQuantity("-3.500000", 1))
	}
}
