package com.axzydev.stock.api.core.idempotency

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F0-07 · unit: keys are 8 to 100 characters of letters, digits, _ and -.
class IdempotencyKeyTest {

	@Test
	fun `accepts keys between 8 and 100 safe characters`() {
		assertTrue(IdempotencyKey.isValid("abcdefgh"))
		assertTrue(IdempotencyKey.isValid("ORDER_2026-10-14_0001"))
		assertTrue(IdempotencyKey.isValid("a".repeat(100)))
	}

	@Test
	fun `rejects short keys, long keys and other symbols`() {
		assertFalse(IdempotencyKey.isValid("abcdefg"))
		assertFalse(IdempotencyKey.isValid("a".repeat(101)))
		assertFalse(IdempotencyKey.isValid("abc def gh"))
		assertFalse(IdempotencyKey.isValid("abc;defgh"))
	}
}
