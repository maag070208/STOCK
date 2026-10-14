package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.core.security.policy.Policy
import java.time.Instant

// An exception for one person. While it is valid it replaces what the roles give, and it can also take access away.
data class PermissionException(val permission: String, val scope: Scope, val expiresAt: Instant?) {
	fun validAt(now: Instant): Boolean = expiresAt == null || expiresAt.isAfter(now)
}

// The permission catalog, the role matrix and the policies, as loaded from the database.
// It is immutable: a write to any of them builds a new snapshot.
class AccessSnapshot(
	private val activePermissions: Set<String>,
	private val activeRoles: Set<String>,
	private val matrix: Map<String, Map<String, Scope>>,
	val policies: List<Policy> = emptyList(),
) {
	// A valid exception decides alone. Without one, the highest scope among the user's active roles wins.
	fun scopeOf(permission: String, roles: Set<String>, exceptions: List<PermissionException>, now: Instant): Scope {
		if (permission !in activePermissions) return Scope.NONE
		val exception = exceptions.firstOrNull { it.permission == permission && it.validAt(now) }
		if (exception != null) return exception.scope
		return Scope.highest(roles.filter { it in activeRoles }.mapNotNull { matrix[it]?.get(permission) })
	}

	// Every permission the user holds above NONE.
	fun scopesOf(roles: Set<String>, exceptions: List<PermissionException>, now: Instant): Map<String, Scope> =
		activePermissions.sorted().associateWith { scopeOf(it, roles, exceptions, now) }.filterValues { it != Scope.NONE }

	companion object {
		// Fail-closed: when the access data cannot be loaded, every scope is NONE.
		val EMPTY = AccessSnapshot(emptySet(), emptySet(), emptyMap())
	}
}
