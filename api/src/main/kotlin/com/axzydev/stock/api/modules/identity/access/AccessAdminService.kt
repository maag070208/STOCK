package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.Patch
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.identity.users.AntiLockout
import com.axzydev.stock.api.modules.identity.users.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

data class PermissionView(
	val key: String,
	val module: String,
	val nameKey: String,
	val scopes: List<String>,
	val sensitive: Boolean,
	val active: Boolean,
	val sortOrder: Int,
)

data class RoleView(val key: String, val name: String?, val system: Boolean, val active: Boolean, val sortOrder: Int, val users: Long)
data class MatrixCell(val roleKey: String, val permissionKey: String, val scope: Scope)
data class CreateRoleRequest(val key: String?, val name: String?)
data class MatrixChange(val roleKey: String?, val permissionKey: String?, val scope: String?)
data class MatrixRequest(val changes: List<MatrixChange>?)

// Roles and the role matrix. Every change is audited, guarded against lockout, and followed by a reload of the access cache.
@Service
class AccessAdminService(
	private val permissions: PermissionRepository,
	private val roles: RoleRepository,
	private val cells: RolePermissionRepository,
	private val users: UserRepository,
	private val cache: AccessCache,
	private val audit: AuditService,
	private val antiLockout: AntiLockout,
	private val clock: Clock,
) {
	@Transactional(readOnly = true)
	fun permissions(): List<PermissionView> = permissions.findAll().sortedBy { it.sortOrder }.map {
		PermissionView(it.key, it.module, it.nameKey, it.scopes, it.sensitive, it.active, it.sortOrder)
	}

	@Transactional(readOnly = true)
	fun roles(): List<RoleView> = roles.findAll().sortedWith(compareBy({ it.sortOrder }, { it.key })).map(::view)

	@Transactional
	fun createRole(actor: CurrentUser, request: CreateRoleRequest): RoleView {
		val validator = Validator()
		val key = validator.required("key", request.key, { if (ROLE_KEY.matches(it)) null else ErrorCode.FORMAT_INVALID }) { it.uppercase() }
		val name = validator.required("name", request.name, Rules::name)
		validator.check()
		if (roles.existsById(key)) throw ValidationException(listOf(FieldError("key", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val role = roles.save(Role(key, name, system = false, active = true, sortOrder = (roles.findAll().maxOfOrNull { it.sortOrder } ?: 0) + 1, createdAt = now, updatedAt = now))
		audit.record(actor.actor, "ROLE_CREATED", "role", key, after = snapshot(role))
		cache.reloadAfterCommit()
		return view(role)
	}

	// A system role keeps its name and stays active. Its order in lists can change.
	@Transactional
	fun updateRole(actor: CurrentUser, key: String, body: Map<String, Any?>): RoleView {
		val patch = Patch(body, setOf("name", "active", "sortOrder"))
		val role = find(key)
		if (role.system && (patch.has("name") || patch.has("active"))) throw ApiException(ErrorCode.SYSTEM_ROLE_PROTECTED)
		val before = snapshot(role)
		val validator = Validator()
		if (patch.has("name")) role.name = validator.required("name", patch.text("name"), Rules::name)
		if (patch.has("active")) role.active = patch.flag("active") ?: validator.reject("active", ErrorCode.FIELD_REQUIRED).let { role.active }
		if (patch.has("sortOrder")) role.sortOrder = patch.number("sortOrder")?.takeIf { it >= 0 } ?: validator.reject("sortOrder", ErrorCode.FORMAT_INVALID).let { role.sortOrder }
		validator.check()
		role.updatedAt = clock.instant()
		audit.record(actor.actor, "ROLE_UPDATED", "role", key, before, snapshot(role))
		antiLockout.check()
		cache.reloadAfterCommit()
		return view(role)
	}

	@Transactional
	fun deleteRole(actor: CurrentUser, key: String) {
		val role = find(key)
		if (role.system) throw ApiException(ErrorCode.SYSTEM_ROLE_PROTECTED)
		if (users.countByRole(key) > 0) throw ApiException(ErrorCode.ROLE_IN_USE)
		val before = snapshot(role)
		// Its matrix cells and its policy links go with it (foreign keys with cascade).
		roles.delete(role)
		audit.record(actor.actor, "ROLE_DELETED", "role", key, before = before)
		antiLockout.check()
		cache.reloadAfterCommit()
	}

	@Transactional(readOnly = true)
	fun matrix(): List<MatrixCell> = cells.findAll()
		.map { MatrixCell(it.id.roleKey, it.id.permissionKey, it.scope) }
		.sortedWith(compareBy({ it.roleKey }, { it.permissionKey }))

	// NONE removes a cell. A scope the permission does not support is rejected.
	@Transactional
	fun changeMatrix(actor: CurrentUser, request: MatrixRequest): List<MatrixCell> {
		val changes = request.changes?.takeIf { it.isNotEmpty() } ?: throw ValidationException(listOf(FieldError("changes", ErrorCode.FIELD_REQUIRED)))
		val roleKeys = roles.findAll().map { it.key }.toSet()
		val validator = Validator()
		val parsed = changes.mapIndexed { index, change ->
			val scope = Scope.entries.firstOrNull { it.name == change.scope }
			val permission = change.permissionKey?.let(PermissionCatalog::find)
			if (change.roleKey !in roleKeys) validator.reject("changes[$index].roleKey", ErrorCode.CATALOG_VALUE_INVALID)
			if (permission == null) validator.reject("changes[$index].permissionKey", ErrorCode.CATALOG_VALUE_INVALID)
			if (scope == null) validator.reject("changes[$index].scope", ErrorCode.FORMAT_INVALID)
			else if (permission != null && scope != Scope.NONE && scope !in permission.scopes) validator.reject("changes[$index].scope", ErrorCode.SCOPE_NOT_SUPPORTED)
			Triple(change.roleKey, change.permissionKey, scope)
		}
		validator.check()

		for ((roleKey, permissionKey, scope) in parsed) {
			val id = RolePermissionId(roleKey!!, permissionKey!!)
			val existing = cells.findById(id).orElse(null)
			val before = existing?.scope ?: Scope.NONE
			if (before == scope) continue
			if (scope == Scope.NONE) cells.delete(existing!!)
			else if (existing == null) cells.save(RolePermission(id, scope!!))
			else existing.scope = scope!!
			audit.record(
				actor.actor, "PERMISSION_MATRIX_CHANGED", "role", roleKey,
				mapOf("permission" to permissionKey, "scope" to before.name), mapOf("permission" to permissionKey, "scope" to scope!!.name),
			)
		}
		antiLockout.check()
		cache.reloadAfterCommit()
		return matrix()
	}

	private fun find(key: String): Role = roles.findById(key).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun view(role: Role) = RoleView(role.key, role.name, role.system, role.active, role.sortOrder, users.countByRole(role.key))

	private fun snapshot(role: Role): Map<String, Any?> = mapOf("key" to role.key, "name" to role.name, "active" to role.active, "sortOrder" to role.sortOrder)

	private companion object {
		val ROLE_KEY = Regex("^[A-Z][A-Z0-9_]{1,39}$")
	}
}
