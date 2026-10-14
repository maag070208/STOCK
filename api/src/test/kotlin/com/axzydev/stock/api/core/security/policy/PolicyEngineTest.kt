package com.axzydev.stock.api.core.security.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F1-12 · unit: policy evaluation, and each operator of access-control.md section 6.
class PolicyEngineTest {

	private val actor = PolicyActor(id = "u-1", username = "ana", roles = setOf("WAREHOUSE_OPERATOR"))

	private fun holds(field: String, operator: String, value: Any?, facts: Map<String, Any?>) =
		PolicyEngine.holds(PolicyCondition(field, operator, value), actor, facts)

	private fun policy(key: String, priority: Int, effect: PolicyEffect, vararg conditions: PolicyCondition, roles: Set<String> = emptySet()) =
		Policy(key, priority, effect, "receipts.receive", conditions.toList(), roles)

	@Test
	fun `with no matching policy the action is allowed`() {
		val decision = PolicyEngine.decide(emptyList(), "receipts.receive", actor, mapOf("countedQuantity" to 5))
		assertEquals(PolicyDecision(PolicyEffect.ALLOW, null), decision)
	}

	@Test
	fun `the first policy by priority whose conditions all hold decides`() {
		val policies = listOf(
			policy("LATER_ALLOW", 20, PolicyEffect.ALLOW, PolicyCondition("countedQuantity", "gt", 100)),
			policy("FIRST_DENY", 10, PolicyEffect.DENY, PolicyCondition("countedQuantity", "gt", 100)),
		)
		assertEquals(PolicyDecision(PolicyEffect.DENY, "FIRST_DENY"), PolicyEngine.decide(policies, "receipts.receive", actor, mapOf("countedQuantity" to 150)))
		assertEquals(PolicyDecision(PolicyEffect.ALLOW, null), PolicyEngine.decide(policies, "receipts.receive", actor, mapOf("countedQuantity" to 50)))
	}

	@Test
	fun `a tie in priority is decided by key`() {
		val policies = listOf(
			policy("B_ALLOW", 10, PolicyEffect.ALLOW),
			policy("A_DENY", 10, PolicyEffect.DENY),
		)
		assertEquals("A_DENY", PolicyEngine.decide(policies, "receipts.receive", actor, emptyMap()).policyKey)
	}

	@Test
	fun `every condition of a policy must hold`() {
		val both = policy("BOTH", 10, PolicyEffect.DENY, PolicyCondition("countedQuantity", "gt", 100), PolicyCondition("difference", "neq", 0))
		assertEquals(PolicyEffect.ALLOW, PolicyEngine.decide(listOf(both), "receipts.receive", actor, mapOf("countedQuantity" to 150, "difference" to 0)).effect)
		assertEquals(PolicyEffect.DENY, PolicyEngine.decide(listOf(both), "receipts.receive", actor, mapOf("countedQuantity" to 150, "difference" to 3)).effect)
	}

	@Test
	fun `a policy applies only to its roles, and to everyone when it names none`() {
		val forManagers = policy("MANAGERS", 10, PolicyEffect.DENY, roles = setOf("WAREHOUSE_MANAGER"))
		val forEveryone = policy("EVERYONE", 10, PolicyEffect.DENY)
		assertEquals(PolicyEffect.ALLOW, PolicyEngine.decide(listOf(forManagers), "receipts.receive", actor, emptyMap()).effect)
		assertEquals(PolicyEffect.DENY, PolicyEngine.decide(listOf(forEveryone), "receipts.receive", actor, emptyMap()).effect)
	}

	@Test
	fun `a policy of another action is not considered`() {
		val other = Policy("OTHER", 10, PolicyEffect.DENY, "orders.cancel", emptyList(), emptySet())
		assertEquals(PolicyEffect.ALLOW, PolicyEngine.decide(listOf(other), "receipts.receive", actor, emptyMap()).effect)
	}

	@Test
	fun `eq and neq compare text exactly and numbers by value`() {
		assertTrue(holds("status", "eq", "CREATED", mapOf("status" to "CREATED")))
		assertFalse(holds("status", "eq", "created", mapOf("status" to "CREATED")))
		assertTrue(holds("quantity", "eq", 10, mapOf("quantity" to java.math.BigDecimal("10.00"))))
		assertTrue(holds("status", "neq", "CANCELLED", mapOf("status" to "CREATED")))
		assertFalse(holds("status", "neq", "CREATED", mapOf("status" to "CREATED")))
	}

	@Test
	fun `in and not_in look for the value in a list, and a list field matches when any element matches`() {
		assertTrue(holds("status", "in", listOf("CREATED", "FULFILLING"), mapOf("status" to "CREATED")))
		assertFalse(holds("status", "in", listOf("VALIDATED"), mapOf("status" to "CREATED")))
		assertTrue(holds("target.roles", "in", listOf("ADMIN", "OFFICE"), mapOf("target.roles" to listOf("OFFICE", "APP_USER"))))
		assertTrue(holds("status", "not_in", listOf("VALIDATED"), mapOf("status" to "CREATED")))
		assertFalse(holds("target.roles", "not_in", listOf("ADMIN"), mapOf("target.roles" to listOf("ADMIN", "OFFICE"))))
	}

	@Test
	fun `contains and not_contains work on text and on lists`() {
		assertTrue(holds("note", "contains", "urgente", mapOf("note" to "pedido urgente")))
		assertFalse(holds("note", "contains", "urgente", mapOf("note" to "pedido normal")))
		assertTrue(holds("target.roles", "contains", "ADMIN", mapOf("target.roles" to listOf("ADMIN"))))
		assertTrue(holds("target.roles", "not_contains", "ADMIN", mapOf("target.roles" to listOf("OFFICE"))))
		assertFalse(holds("target.roles", "not_contains", "ADMIN", mapOf("target.roles" to listOf("ADMIN"))))
	}

	@Test
	fun `gt, gte, lt and lte compare numbers`() {
		val facts = mapOf("countedQuantity" to 100)
		assertFalse(holds("countedQuantity", "gt", 100, facts))
		assertTrue(holds("countedQuantity", "gte", 100, facts))
		assertFalse(holds("countedQuantity", "lt", 100, facts))
		assertTrue(holds("countedQuantity", "lte", 100, facts))
		assertTrue(holds("countedQuantity", "gt", 99.5, facts))
		assertTrue(holds("countedQuantity", "lt", 100.5, facts))
	}

	@Test
	fun `exists tells whether the field is present`() {
		assertTrue(holds("locationId", "exists", true, mapOf("locationId" to "K05A")))
		assertFalse(holds("locationId", "exists", true, emptyMap()))
		assertTrue(holds("locationId", "exists", false, emptyMap()))
	}

	@Test
	fun `a missing field fails a comparison, and passes the negative operators`() {
		val facts = emptyMap<String, Any?>()
		assertFalse(holds("quantity", "eq", 1, facts))
		assertFalse(holds("quantity", "gt", 1, facts))
		assertFalse(holds("quantity", "in", listOf(1), facts))
		assertTrue(holds("quantity", "neq", 1, facts))
		assertTrue(holds("quantity", "not_in", listOf(1), facts))
	}

	@Test
	fun `a value can refer to the person who acts`() {
		assertTrue(holds("target.id", "eq", "@user.id", mapOf("target.id" to "u-1")))
		assertTrue(holds("createdBy", "eq", "@user.username", mapOf("createdBy" to "ana")))
		assertTrue(holds("requiredRole", "in", "@user.roles", mapOf("requiredRole" to "WAREHOUSE_OPERATOR")))
	}

	@Test
	fun `an unknown operator never holds`() {
		assertFalse(holds("quantity", "between", 1, mapOf("quantity" to 1)))
	}
}
