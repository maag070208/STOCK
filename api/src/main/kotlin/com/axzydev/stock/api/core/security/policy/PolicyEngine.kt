package com.axzydev.stock.api.core.security.policy

import java.math.BigDecimal

enum class PolicyEffect { ALLOW, DENY }

// `value` is parsed JSON: text, a number, a boolean, a list or null.
data class PolicyCondition(val field: String, val operator: String, val value: Any?)

data class Policy(
	val key: String,
	val priority: Int,
	val effect: PolicyEffect,
	val action: String,
	val conditions: List<PolicyCondition>,
	// Empty means every role.
	val roles: Set<String>,
)

data class PolicyActor(val id: String, val username: String, val roles: Set<String>)

// `policyKey` is null when no policy matched.
data class PolicyDecision(val effect: PolicyEffect, val policyKey: String?)

// ABAC, as in access-control.md sections 5 and 6. It runs after RBAC has allowed the action.
object PolicyEngine {

	val OPERATORS = setOf("eq", "neq", "in", "not_in", "contains", "not_contains", "gt", "gte", "lt", "lte", "exists")

	// The first policy by priority, then key, that applies to the actor and whose conditions all hold decides.
	// With no match the action is allowed, because RBAC already granted it.
	fun decide(policies: List<Policy>, action: String, actor: PolicyActor, facts: Map<String, Any?>): PolicyDecision {
		val match = policies
			.filter { it.action == action && (it.roles.isEmpty() || it.roles.any(actor.roles::contains)) }
			.sortedWith(compareBy({ it.priority }, { it.key }))
			.firstOrNull { policy -> policy.conditions.all { holds(it, actor, facts) } }
		return if (match == null) PolicyDecision(PolicyEffect.ALLOW, null) else PolicyDecision(match.effect, match.key)
	}

	fun holds(condition: PolicyCondition, actor: PolicyActor, facts: Map<String, Any?>): Boolean {
		val actual = facts[condition.field]
		val expected = reference(condition.value, actor)
		return when (condition.operator) {
			"exists" -> (actual != null) == (expected != false)
			"eq" -> actual != null && same(actual, expected)
			"neq" -> actual == null || !same(actual, expected)
			"in" -> actual != null && isIn(actual, expected)
			"not_in" -> actual == null || !isIn(actual, expected)
			"contains" -> actual != null && contains(actual, expected)
			"not_contains" -> actual == null || !contains(actual, expected)
			"gt" -> compare(actual, expected)?.let { it > 0 } ?: false
			"gte" -> compare(actual, expected)?.let { it >= 0 } ?: false
			"lt" -> compare(actual, expected)?.let { it < 0 } ?: false
			"lte" -> compare(actual, expected)?.let { it <= 0 } ?: false
			else -> false
		}
	}

	// "@user.id", "@user.username" and "@user.roles" stand for the person who acts.
	private fun reference(value: Any?, actor: PolicyActor): Any? =
		when (value) {
			"@user.id" -> actor.id
			"@user.username" -> actor.username
			"@user.roles" -> actor.roles.toList()
			else -> value
		}

	private fun number(value: Any?): BigDecimal? =
		when (value) {
			is BigDecimal -> value
			is Number -> BigDecimal(value.toString())
			else -> null
		}

	private fun same(a: Any?, b: Any?): Boolean {
		val (x, y) = number(a) to number(b)
		return if (x != null && y != null) x.compareTo(y) == 0 else a == b
	}

	private fun list(value: Any?): List<Any?> = if (value is Collection<*>) value.toList() else listOf(value)

	// A list field is "in" the expected list when any of its elements is.
	private fun isIn(actual: Any, expected: Any?): Boolean = list(actual).any { item -> list(expected).any { same(item, it) } }

	private fun contains(actual: Any, expected: Any?): Boolean =
		when (actual) {
			is Collection<*> -> actual.any { same(it, expected) }
			is String -> expected is String && actual.contains(expected)
			else -> false
		}

	private fun compare(actual: Any?, expected: Any?): Int? {
		val (x, y) = number(actual) to number(expected)
		if (x != null && y != null) return x.compareTo(y)
		return if (actual is String && expected is String) actual.compareTo(expected) else null
	}
}
