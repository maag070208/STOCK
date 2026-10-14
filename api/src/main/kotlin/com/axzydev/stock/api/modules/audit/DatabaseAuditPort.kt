package com.axzydev.stock.api.modules.audit

import com.axzydev.stock.api.core.audit.AuditEvent
import com.axzydev.stock.api.core.audit.AuditPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

// Writes the audit log. It joins the caller's transaction when there is one.
@Component
class DatabaseAuditPort(private val records: AuditRecordRepository, private val json: JsonMapper) : AuditPort {

	@Transactional
	override fun write(event: AuditEvent) {
		records.save(
			AuditRecord(
				occurredAt = event.occurredAt,
				actorId = event.actor.id,
				actor = event.actor.name,
				action = event.action,
				targetType = event.targetType,
				targetId = event.targetId,
				before = event.before?.let(json::writeValueAsString),
				after = event.after?.let(json::writeValueAsString),
			),
		)
	}
}
