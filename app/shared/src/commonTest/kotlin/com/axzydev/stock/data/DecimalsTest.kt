package com.axzydev.stock.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The arithmetic on the decimal texts the API writes is exact: a quantity is never read as a floating point number.
class DecimalsTest {

	@Test
	fun theSumKeepsTheLargestScale() {
		assertEquals("80.000000", Decimals.sum(listOf("50", "30.000000")))
		assertEquals("3.50", Decimals.sum(listOf("1.25", "2.25")))
		assertEquals("0", Decimals.sum(emptyList()))
	}

	@Test
	fun theSubtractionIsExactAndNeverNegative() {
		assertEquals("30.000000", Decimals.subtract("80.000000", "50"))
		assertEquals("0.000000", Decimals.subtract("80.000000", "80"))
		assertNull(Decimals.subtract("50", "80"))
	}

	@Test
	fun theSameQuantityReadsTheSameWhateverTheDigits() {
		assertTrue(Decimals.same("80.000000", "80"))
		assertTrue(Decimals.same("80", "80.000000"))
		assertFalse(Decimals.same("80.000001", "80"))
	}

	@Test
	fun aQuantityIsNonNegativeAndNumeric() {
		assertTrue(Decimals.isValid("0"))
		assertTrue(Decimals.isValid("10.5"))
		assertTrue(Decimals.isValid("500.000000"))
		assertFalse(Decimals.isValid("-5"))
		assertFalse(Decimals.isValid("10.5.5"))
		assertFalse(Decimals.isValid("diez"))
		assertFalse(Decimals.isValid(""))
	}

	@Test
	fun theComparisonIgnoresTheDigitsEachOneCarries() {
		assertTrue(Decimals.above("80.000001", "80"))
		assertFalse(Decimals.above("80", "80.000000"))
		assertFalse(Decimals.above("50", "80"))
		// Something that is not a quantity is above nothing.
		assertFalse(Decimals.above("diez", "80"))
		assertFalse(Decimals.above("80", ""))
	}
}
