package com.axzydev.stock.api.core.security.policy

import org.springframework.stereotype.Component

// An action a policy can restrict, with the fields a condition may read. This is the security boundary:
// a policy cannot read what the action does not expose. Each module declares its actions as beans.
data class PolicyAction(val key: String, val fields: Set<String>)

@Component
class PolicyActionRegistry(actions: List<PolicyAction>) {

	val all: List<PolicyAction> = actions.sortedBy { it.key }

	fun find(key: String): PolicyAction? = all.firstOrNull { it.key == key }
}
