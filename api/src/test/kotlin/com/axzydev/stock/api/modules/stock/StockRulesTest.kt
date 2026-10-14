package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

// F4-01 and F4-02 · unit: the quantity of a movement, and what a movement does to a balance.
class StockRulesTest {

	@Test
	fun `a movement's quantity is positive, at most 999999, with no more decimals than its unit allows`() {
		assertNull(StockRules.quantity(BigDecimal("500"), decimals = 0))
		assertNull(StockRules.quantity(BigDecimal("1.25"), decimals = 2))
		assertEquals(ErrorCode.QUANTITY_INVALID, StockRules.quantity(BigDecimal.ZERO, decimals = 0))
		assertEquals(ErrorCode.QUANTITY_INVALID, StockRules.quantity(BigDecimal("-1"), decimals = 0))
		assertEquals(ErrorCode.QUANTITY_INVALID, StockRules.quantity(BigDecimal("1.5"), decimals = 0))
		assertEquals(ErrorCode.QUANTITY_INVALID, StockRules.quantity(BigDecimal("1000000"), decimals = 0))
		// Trailing zeros are not decimals: 2.50 is 2.5.
		assertNull(StockRules.quantity(BigDecimal("2.50"), decimals = 1))
	}

	@Test
	fun `a movement in raises the balance, and one out lowers it`() {
		assertEquals(BigDecimal("15"), StockRules.apply(BigDecimal("10"), StockRules.Direction.IN, BigDecimal("5")))
		assertEquals(BigDecimal("4"), StockRules.apply(BigDecimal("10"), StockRules.Direction.OUT, BigDecimal("6")))
		assertEquals(BigDecimal("0"), StockRules.apply(BigDecimal("10"), StockRules.Direction.OUT, BigDecimal("10")))
	}

	@Test
	fun `a movement out above the balance is refused`() {
		val refused = assertThrows<ApiException> { StockRules.apply(BigDecimal("10"), StockRules.Direction.OUT, BigDecimal("11")) }
		assertEquals(ErrorCode.STOCK_INSUFFICIENT, refused.code)
		assertEquals(listOf("10"), refused.args.toList())
	}

	@Test
	fun `the direction of each type is read from its catalog attribute`() {
		assertEquals(StockRules.Direction.IN, StockRules.direction(mapOf("direction" to "in")))
		assertEquals(StockRules.Direction.OUT, StockRules.direction(mapOf("direction" to "out")))
		assertNull(StockRules.direction(mapOf()))
	}
}
