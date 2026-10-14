package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.modules.products.LegacyFile
import com.axzydev.stock.api.modules.products.LegacyProduct
import com.axzydev.stock.api.modules.products.RejectedRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// F4-03 and F4-04 · unit: the plan of the opening inventory. Its total is the sum of the balances it reads; products
// with zero stock need no movement; a product that does not exist is reported; the control total decides.
class OpeningInventoryTest {

	private val a = UUID.randomUUID()
	private val b = UUID.randomUUID()

	private fun file(vararg stocks: Pair<String, Int>, control: Long?) = LegacyFile(
		products = stocks.mapIndexed { i, (code, stock) -> LegacyProduct(i + 2, i + 1, code, "Producto $code", "PIECE", BigDecimal(stock)) },
		rejected = listOf(RejectedRow(99, "code", ErrorCode.FIELD_REQUIRED)),
		stockTotal = BigDecimal(stocks.sumOf { it.second }),
		controlTotal = control,
	)

	@Test
	fun `the total is the sum of the balances read, and zero stock needs no movement`() {
		val plan = OpeningInventory.plan(file("A" to 300, "B" to 0, "C" to 200, control = 500), mapOf("A" to a, "B" to b, "C" to UUID.randomUUID()))
		assertEquals(3, plan.products)
		assertEquals(2, plan.lines.size)
		assertEquals(0, BigDecimal(500).compareTo(plan.total))
		assertEquals(0, plan.total.compareTo(plan.lines.sumOf { it.quantity }))
		assertTrue(plan.controlMatches!!)
	}

	@Test
	fun `a product that does not exist is reported, and its stock is not in the total, so the control fails`() {
		val plan = OpeningInventory.plan(file("A" to 300, "X" to 200, control = 500), mapOf("A" to a))
		assertEquals(listOf("X"), plan.missing.map { it.code })
		assertEquals(0, BigDecimal(300).compareTo(plan.total))
		assertEquals(false, plan.controlMatches)
	}

	@Test
	fun `without a control total there is nothing to match`() {
		assertEquals(null, OpeningInventory.plan(file("A" to 1, control = null), mapOf("A" to a)).controlMatches)
	}
}
