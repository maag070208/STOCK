package com.axzydev.stock.api.core.idempotency

// Idempotency-Key rule from api.md section 2: 8 to 100 characters of letters, digits, _ and -.
object IdempotencyKey {
	private val pattern = Regex("^[A-Za-z0-9_-]{8,100}$")

	fun isValid(key: String): Boolean = pattern.matches(key)
}
