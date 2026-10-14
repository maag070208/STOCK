package com.axzydev.stock.api.core.idempotency

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

// F0-07 · unit: the first response is stored, a repeat with the same request gets it back, a different request is refused.
class IdempotencyServiceTest {

	private val store = mockk<IdempotencyStore>()
	private val service = IdempotencyService(store)

	@Test
	fun `a new key runs the action and stores its response`() {
		every { store.find("key-0001") } returns null
		every { store.save("key-0001", any(), any()) } returns Unit
		val result = service.execute("key-0001", "hash-a") { StoredResponse(201, "{\"id\":1}") }
		assertEquals("{\"id\":1}", result.body)
		verify(exactly = 1) { store.save("key-0001", "hash-a", StoredResponse(201, "{\"id\":1}")) }
	}

	@Test
	fun `a repeat with the same request returns the stored response without running the action`() {
		every { store.find("key-0002") } returns Stored("hash-a", StoredResponse(201, "{\"id\":2}"))
		var ran = false
		val result = service.execute("key-0002", "hash-a") { ran = true; StoredResponse(201, "{}") }
		assertEquals("{\"id\":2}", result.body)
		assertEquals(false, ran)
	}

	@Test
	fun `the same key with a different request is refused`() {
		every { store.find("key-0003") } returns Stored("hash-a", StoredResponse(201, "{}"))
		val error = assertThrows(ApiException::class.java) {
			service.execute("key-0003", "hash-b") { StoredResponse(201, "{}") }
		}
		assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, error.code)
	}
}
