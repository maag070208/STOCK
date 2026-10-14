package com.axzydev.stock.api.core.audit

import com.axzydev.stock.api.core.security.Actor
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// F0-08 and F1-17 · unit: recording an action calls the audit port once, and the record carries
// the actor, the action, the target, the values before and after, and the time.
class AuditServiceTest {

	@Test
	fun `the record carries actor, action, target, before, after and time`() {
		val port = mockk<AuditPort>(relaxed = true)
		val now = Instant.parse("2026-10-13T12:00:00Z")
		val actor = Actor(UUID.randomUUID(), "ana")
		val event = slot<AuditEvent>()

		AuditService(port, Clock.fixed(now, ZoneOffset.UTC))
			.record(actor, "USER_UPDATED", "user", "u-1", before = mapOf("name" to "Ana"), after = mapOf("name" to "Ana López"))

		verify(exactly = 1) { port.write(capture(event)) }
		assertEquals(actor, event.captured.actor)
		assertEquals("USER_UPDATED", event.captured.action)
		assertEquals("user", event.captured.targetType)
		assertEquals("u-1", event.captured.targetId)
		assertEquals(mapOf("name" to "Ana"), event.captured.before)
		assertEquals(mapOf("name" to "Ana López"), event.captured.after)
		assertEquals(now, event.captured.occurredAt)
	}
}
