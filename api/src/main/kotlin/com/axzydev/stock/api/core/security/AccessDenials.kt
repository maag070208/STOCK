package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.core.audit.AuditService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// Every refusal by channel, permission or policy is written to the audit log as ACCESS_DENIED.
@Component
class AccessDenials(private val audit: AuditService) {

	// A refusal has no change to commit with, and the request is about to fail. The record gets its own transaction,
	// so it stays when the request's transaction rolls back.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun record(actor: Actor, denied: String, detail: Map<String, Any?> = emptyMap()) {
		audit.record(actor, ACTION, "access", denied, after = detail)
	}

	companion object {
		const val ACTION = "ACCESS_DENIED"
	}
}
