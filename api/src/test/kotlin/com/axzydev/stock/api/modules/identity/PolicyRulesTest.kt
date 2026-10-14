package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.policy.PolicyAction
import com.axzydev.stock.api.core.security.policy.PolicyActionRegistry
import com.axzydev.stock.api.modules.identity.access.ConditionBody
import com.axzydev.stock.api.modules.identity.access.PolicyBody
import com.axzydev.stock.api.modules.identity.access.PolicyRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// F1-13 · unit: what a policy may contain. The action registry is the security boundary.
class PolicyRulesTest {

	private val registry = PolicyActionRegistry(listOf(PolicyAction("receipts.receive", setOf("countedQuantity", "difference"))))
	private val roles = setOf("ADMIN", "WAREHOUSE_OPERATOR")

	private val valid = PolicyBody(
		key = "RECEIPT_QUANTITY_LIMIT", priority = 10, effect = "DENY", action = "receipts.receive",
		conditions = listOf(ConditionBody("countedQuantity", "gt", 100)), roles = listOf("WAREHOUSE_OPERATOR"),
	)

	private fun errors(body: PolicyBody) = PolicyRules.check(body, registry, roles).map { it.field to it.code }

	@Test
	fun `the example policy of the access rules is valid`() {
		assertEquals(emptyList<Any>(), errors(valid))
	}

	@Test
	fun `a policy that names an unregistered action is rejected`() {
		assertEquals(listOf("action" to ErrorCode.POLICY_ACTION_UNKNOWN), errors(valid.copy(action = "orders.vanish", conditions = emptyList())))
	}

	@Test
	fun `a policy cannot target roles manage, because it is not a registered action`() {
		assertEquals(listOf("action" to ErrorCode.POLICY_ACTION_UNKNOWN), errors(valid.copy(action = "roles.manage", conditions = emptyList())))
	}

	@Test
	fun `a condition cannot read a field the action does not expose`() {
		val body = valid.copy(conditions = listOf(ConditionBody("supplierTaxId", "eq", "XAXX010101000")))
		assertEquals(listOf("conditions[0].field" to ErrorCode.POLICY_FIELD_UNKNOWN), errors(body))
	}

	@Test
	fun `key, priority, effect, operator, value and roles are all checked`() {
		val body = PolicyBody(
			key = "lower case", priority = -1, effect = "MAYBE", action = "receipts.receive",
			conditions = listOf(ConditionBody("difference", "between", 1), ConditionBody("countedQuantity", "eq", mapOf("nested" to 1)), ConditionBody("difference", "exists")),
			roles = listOf("NO_SUCH_ROLE"),
		)
		assertEquals(
			listOf(
				"key" to ErrorCode.FORMAT_INVALID,
				"priority" to ErrorCode.FORMAT_INVALID,
				"effect" to ErrorCode.FORMAT_INVALID,
				"conditions[0].operator" to ErrorCode.FORMAT_INVALID,
				"conditions[1].value" to ErrorCode.FORMAT_INVALID,
				"roles" to ErrorCode.CATALOG_VALUE_INVALID,
			),
			errors(body),
		)
		assertEquals(listOf("key" to ErrorCode.FIELD_REQUIRED, "priority" to ErrorCode.FIELD_REQUIRED, "effect" to ErrorCode.FIELD_REQUIRED, "action" to ErrorCode.FIELD_REQUIRED), errors(PolicyBody()))
	}

	@Test
	fun `the same field and operator cannot repeat in one policy`() {
		val body = valid.copy(conditions = listOf(ConditionBody("countedQuantity", "gt", 100), ConditionBody("countedQuantity", "gt", 200)))
		assertEquals(listOf("conditions" to ErrorCode.DUPLICATE_VALUE), errors(body))
	}
}
