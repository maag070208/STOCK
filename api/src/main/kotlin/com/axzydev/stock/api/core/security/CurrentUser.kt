package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import java.util.UUID

// Who did something, for the audit log.
data class Actor(val id: UUID?, val name: String) {
	companion object {
		val SYSTEM = Actor(null, "system")
	}
}

// The signed-in person of a request, with the access resolved from the database for this request.
class CurrentUser(
	val id: UUID,
	val username: String,
	val roles: Set<String>,
	val warehouseIds: Set<UUID>,
	val permissions: Map<String, Scope>,
	val mustChangePassword: Boolean,
) {
	val actor: Actor get() = Actor(id, username)

	fun scopeOf(permission: String): Scope = permissions[permission] ?: Scope.NONE

	fun can(permission: String): Boolean = scopeOf(permission) != Scope.NONE

	// True when a record is inside the user's scope for the permission:
	// ALL reaches every record, AREA the records of the user's warehouses, OWN the records the user created or is assigned to.
	fun reaches(permission: String, warehouseId: UUID? = null, ownerIds: Set<UUID> = emptySet()): Boolean =
		when (scopeOf(permission)) {
			Scope.ALL -> true
			Scope.AREA -> warehouseId != null && warehouseId in warehouseIds
			Scope.OWN -> id in ownerIds
			Scope.NONE -> false
		}

	// A read by id outside the scope answers as if the record did not exist.
	fun requireRead(permission: String, warehouseId: UUID? = null, ownerIds: Set<UUID> = emptySet()) {
		if (!reaches(permission, warehouseId, ownerIds)) throw ApiException(ErrorCode.NOT_FOUND)
	}

	// A write outside the scope is refused.
	fun requireWrite(permission: String, warehouseId: UUID? = null, ownerIds: Set<UUID> = emptySet()) {
		if (!reaches(permission, warehouseId, ownerIds)) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
	}
}

// What the request checks need to know about an account. The identity module provides it.
class LoadedAccount(val user: CurrentUser, val active: Boolean)

interface AccountLoader {
	fun load(userId: UUID): LoadedAccount?
}
