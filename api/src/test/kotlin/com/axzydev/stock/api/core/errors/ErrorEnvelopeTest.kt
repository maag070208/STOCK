package com.axzydev.stock.api.core.errors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.Locale

// F0-04 · unit: an exception maps to the flat envelope with the right status.
class ErrorEnvelopeTest {

	private val handler = GlobalExceptionHandler(StockMessages.source())

	@Test
	fun `a known error maps to its status, code and a message`() {
		val response = handler.handleApi(ApiException(ErrorCode.STOCK_INSUFFICIENT), Locale("es"))
		assertEquals(409, response.statusCode.value())
		assertEquals("STOCK_INSUFFICIENT", response.body!!.code)
		assertFalse(response.body!!.message.isBlank())
	}

	@Test
	fun `an unknown exception maps to INTERNAL_ERROR without a stack trace`() {
		val response = handler.handleUnexpected(RuntimeException("secret detail"), Locale("es"))
		assertEquals(500, response.statusCode.value())
		assertEquals("INTERNAL_ERROR", response.body!!.code)
		assertFalse(response.body!!.message.contains("secret detail"))
	}
}
