package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.policy.PolicyAction
import com.axzydev.stock.api.core.security.policy.PolicyActionRegistry
import com.axzydev.stock.api.core.security.policy.PolicyEngine
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

data class ConditionBody(val field: String? = null, val operator: String? = null, val value: Any? = null)

data class PolicyBody(
	val key: String? = null,
	val priority: Int? = null,
	val effect: String? = null,
	val action: String? = null,
	val active: Boolean? = null,
	val conditions: List<ConditionBody>? = null,
	val roles: List<String>? = null,
)

data class PolicyView(
	val key: String,
	val priority: Int,
	val effect: String,
	val action: String,
	val active: Boolean,
	val conditions: List<ConditionBody>,
	val roles: List<String>,
)

// What a policy may contain. The action registry is the security boundary:
// a policy cannot name an action that is not registered, and a condition cannot read a field the action does not expose.
// `roles.manage` is not a registered action, so no policy can restrict the management of access itself.
object PolicyRules {

	private val KEY = Regex("^[A-Z][A-Z0-9_]{1,59}$")

	fun check(body: PolicyBody, registry: PolicyActionRegistry, roleKeys: Set<String>): List<FieldError> = buildList {
		if (body.key == null) add(FieldError("key", ErrorCode.FIELD_REQUIRED)) else if (!KEY.matches(body.key)) add(FieldError("key", ErrorCode.FORMAT_INVALID))
		if (body.priority == null) add(FieldError("priority", ErrorCode.FIELD_REQUIRED)) else if (body.priority !in 0..9999) add(FieldError("priority", ErrorCode.FORMAT_INVALID))
		if (body.effect == null) add(FieldError("effect", ErrorCode.FIELD_REQUIRED)) else if (body.effect != "ALLOW" && body.effect != "DENY") add(FieldError("effect", ErrorCode.FORMAT_INVALID))
		val action = body.action?.let(registry::find)
		if (body.action == null) add(FieldError("action", ErrorCode.FIELD_REQUIRED)) else if (action == null) add(FieldError("action", ErrorCode.POLICY_ACTION_UNKNOWN))
		body.conditions.orEmpty().forEachIndexed { index, condition -> addAll(condition(index, condition, action)) }
		val pairs = body.conditions.orEmpty().map { it.field to it.operator }
		if (pairs.size != pairs.toSet().size) add(FieldError("conditions", ErrorCode.DUPLICATE_VALUE))
		if (body.roles.orEmpty().any { it !in roleKeys }) add(FieldError("roles", ErrorCode.CATALOG_VALUE_INVALID))
	}

	private fun condition(index: Int, condition: ConditionBody, action: PolicyAction?): List<FieldError> = buildList {
		val at = "conditions[$index]"
		if (condition.field.isNullOrBlank()) add(FieldError("$at.field", ErrorCode.FIELD_REQUIRED))
		else if (action != null && condition.field !in action.fields) add(FieldError("$at.field", ErrorCode.POLICY_FIELD_UNKNOWN))
		if (condition.operator !in PolicyEngine.OPERATORS) add(FieldError("$at.operator", ErrorCode.FORMAT_INVALID))
		// A value is text, a number, a boolean, or a list of those. Only `exists` works without one.
		val scalar = { value: Any? -> value is String || value is Number || value is Boolean }
		val valid = when (val value = condition.value) {
			null -> condition.operator == "exists"
			is List<*> -> value.all(scalar)
			else -> scalar(value)
		}
		if (!valid) add(FieldError("$at.value", ErrorCode.FORMAT_INVALID))
	}
}

@Service
class PolicyAdminService(
	private val policies: PolicyRepository,
	private val roles: RoleRepository,
	private val registry: PolicyActionRegistry,
	private val cache: AccessCache,
	private val audit: AuditService,
	private val json: JsonMapper,
	private val clock: Clock,
) {
	@Transactional(readOnly = true)
	fun list(): List<PolicyView> = policies.findAll().sortedWith(compareBy({ it.priority }, { it.key })).map(::view)

	fun actions(): List<PolicyAction> = registry.all

	@Transactional
	fun create(actor: CurrentUser, body: PolicyBody): PolicyView {
		validate(body)
		if (policies.existsById(body.key!!)) throw ValidationException(listOf(FieldError("key", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val row = PolicyRow(body.key, body.priority!!, body.effect!!, body.action!!, body.active ?: true, now, now)
		fill(row, body)
		val saved = policies.save(row)
		audit.record(actor.actor, "POLICY_CREATED", "policy", saved.key, after = view(saved))
		cache.reloadAfterCommit()
		return view(saved)
	}

	// The body replaces the policy: every field except the key is sent again.
	@Transactional
	fun update(actor: CurrentUser, key: String, body: PolicyBody): PolicyView {
		val row = policies.findById(key).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		if (body.key != null && body.key != key) throw ValidationException(listOf(FieldError("key", ErrorCode.FORMAT_INVALID)))
		val complete = body.copy(key = key)
		validate(complete)
		val before = view(row)
		row.priority = complete.priority!!
		row.effect = complete.effect!!
		row.action = complete.action!!
		row.active = complete.active ?: row.active
		row.updatedAt = clock.instant()
		fill(row, complete)
		audit.record(actor.actor, "POLICY_UPDATED", "policy", key, before, view(row))
		cache.reloadAfterCommit()
		return view(row)
	}

	@Transactional
	fun delete(actor: CurrentUser, key: String) {
		val row = policies.findById(key).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		val before = view(row)
		policies.delete(row)
		audit.record(actor.actor, "POLICY_DELETED", "policy", key, before = before)
		cache.reloadAfterCommit()
	}

	private fun validate(body: PolicyBody) {
		val errors = PolicyRules.check(body, registry, roles.findAll().map { it.key }.toSet())
		if (errors.isNotEmpty()) throw ValidationException(errors)
	}

	private fun fill(row: PolicyRow, body: PolicyBody) {
		row.conditions.clear()
		row.conditions.addAll(body.conditions.orEmpty().map { PolicyConditionRow(it.field!!, it.operator!!, json.writeValueAsString(it.value ?: true)) })
		row.roles.clear()
		row.roles.addAll(body.roles.orEmpty())
	}

	private fun view(row: PolicyRow) = PolicyView(
		key = row.key,
		priority = row.priority,
		effect = row.effect,
		action = row.action,
		active = row.active,
		conditions = row.conditions.map { ConditionBody(it.field, it.operator, json.readValue(it.value, Any::class.java)) },
		roles = row.roles.sorted(),
	)
}
