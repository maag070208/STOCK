package com.axzydev.stock.api.core.audit

import com.axzydev.stock.api.modules.audit.DatabaseAuditPort
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

// Test adapter: keeps the events so a test can check what was audited, and passes them on to the database.
@Component
@Primary
class RecordingAuditPort(private val database: DatabaseAuditPort) : AuditPort {
	val events = CopyOnWriteArrayList<AuditEvent>()

	override fun write(event: AuditEvent) {
		events += event
		database.write(event)
	}

	fun clear() = events.clear()
}
