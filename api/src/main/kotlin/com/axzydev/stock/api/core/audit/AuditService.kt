package com.axzydev.stock.api.core.audit

import com.axzydev.stock.api.core.security.Actor
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class AuditService(private val port: AuditPort, private val clock: Clock) {

	// Called inside the transaction of the change, so the record and the change succeed or fail together.
	fun record(actor: Actor, action: String, targetType: String, targetId: String, before: Any? = null, after: Any? = null) {
		port.write(AuditEvent(actor, action, targetType, targetId, before, after, clock.instant()))
	}
}
