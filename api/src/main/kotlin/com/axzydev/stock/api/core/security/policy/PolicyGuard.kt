package com.axzydev.stock.api.core.security.policy

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.AccessDenials
import com.axzydev.stock.api.core.security.AccessSnapshot
import com.axzydev.stock.api.core.security.CurrentUser
import org.springframework.stereotype.Component

// Where the current role matrix and policies come from. The identity module provides it.
interface AccessSnapshotSource {
	val snapshot: AccessSnapshot
}

// A service calls this after RBAC allowed the action, with the facts the action exposes.
@Component
class PolicyGuard(private val source: AccessSnapshotSource, private val denials: AccessDenials) {

	fun enforce(action: String, user: CurrentUser, facts: Map<String, Any?>) {
		val actor = PolicyActor(user.id.toString(), user.username, user.roles)
		val decision = PolicyEngine.decide(source.snapshot.policies, action, actor, facts)
		if (decision.effect == PolicyEffect.DENY) {
			denials.record(user.actor, action, mapOf("policy" to decision.policyKey))
			throw ApiException(ErrorCode.POLICY_DENIED)
		}
	}
}
