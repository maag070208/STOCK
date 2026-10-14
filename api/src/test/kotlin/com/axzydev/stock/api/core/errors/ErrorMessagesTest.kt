package com.axzydev.stock.api.core.errors

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.Locale

// F0-05 · unit: every error code has a message in es and in en.
class ErrorMessagesTest {

	@Test
	fun `every error code has a Spanish and an English message`() {
		val source = StockMessages.source()
		ErrorCode.entries.forEach { code ->
			listOf(Locale("es"), Locale("en")).forEach { locale ->
				val message = source.getMessage(code.messageKey, null, locale)
				assertFalse(message.isBlank(), "missing ${locale.language} message for ${code.name}")
				assertFalse(message.startsWith("error."), "unresolved key for ${code.name} in ${locale.language}")
			}
		}
	}
}
