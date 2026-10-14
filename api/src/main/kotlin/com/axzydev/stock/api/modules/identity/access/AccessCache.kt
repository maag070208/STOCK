package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.security.AccessSnapshot
import com.axzydev.stock.api.core.security.policy.AccessSnapshotSource
import com.axzydev.stock.api.core.security.policy.Policy
import com.axzydev.stock.api.core.security.policy.PolicyCondition
import com.axzydev.stock.api.core.security.policy.PolicyEffect
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

// The permission catalog, the role matrix and the policies, kept in memory and loaded again after every write to them.
// What belongs to one person (roles, exceptions, warehouses) is not cached: it is read on every request.
@Component
class AccessCache(
	private val permissions: PermissionRepository,
	private val roles: RoleRepository,
	private val cells: RolePermissionRepository,
	private val policies: PolicyRepository,
	private val json: JsonMapper,
	private val transactions: TransactionTemplate,
) : AccessSnapshotSource {
	private val log = LoggerFactory.getLogger(javaClass)

	@Volatile
	private var current: AccessSnapshot = AccessSnapshot.EMPTY

	override val snapshot: AccessSnapshot get() = current

	@EventListener(ApplicationReadyEvent::class)
	fun reload() {
		current = try {
			transactions.execute { load() } ?: AccessSnapshot.EMPTY
		} catch (e: Exception) {
			// Fail-closed: without the access data every scope is NONE.
			log.error("the access data could not be loaded; every permission is denied until it loads", e)
			AccessSnapshot.EMPTY
		}
	}

	// For services that change the matrix, a role or a policy: the new data is read once their transaction has committed.
	fun reloadAfterCommit() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return reload()
		TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
			override fun afterCommit() = reload()
		})
	}

	private fun load(): AccessSnapshot {
		val matrix = cells.findAll().groupBy({ it.id.roleKey }, { it.id.permissionKey to it.scope }).mapValues { it.value.toMap() }
		return AccessSnapshot(
			activePermissions = permissions.findAll().filter { it.active }.map { it.key }.toSet(),
			activeRoles = roles.findAll().filter { it.active }.map { it.key }.toSet(),
			matrix = matrix,
			policies = policies.findAll().filter { it.active }.map(::policy),
		)
	}

	private fun policy(row: PolicyRow) = Policy(
		key = row.key,
		priority = row.priority,
		effect = PolicyEffect.valueOf(row.effect),
		action = row.action,
		conditions = row.conditions.map { PolicyCondition(it.field, it.operator, json.readValue(it.value, Any::class.java)) },
		roles = row.roles.toSet(),
	)
}
