package com.axzydev.stock.api.core.audit

import com.axzydev.stock.api.core.security.Actor
import java.time.Instant

// Port for the audit log (CYC D-016). Modules record through AuditService; the audit module writes to the database.
interface AuditPort {
	fun write(event: AuditEvent)
}

// `before` and `after` are plain values (maps, lists, text, numbers) that describe the record. Never a password or its hash.
data class AuditEvent(
	val actor: Actor,
	val action: String,
	val targetType: String,
	val targetId: String,
	val before: Any? = null,
	val after: Any? = null,
	val occurredAt: Instant,
)
