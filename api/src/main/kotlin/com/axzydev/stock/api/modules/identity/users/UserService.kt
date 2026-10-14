package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Passwords
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.security.policy.PolicyGuard
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.Patch
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.identity.access.PermissionCatalog
import com.axzydev.stock.api.modules.identity.access.RoleRepository
import com.axzydev.stock.api.modules.identity.access.UserPermission
import com.axzydev.stock.api.modules.identity.access.UserPermissionRepository
import com.axzydev.stock.api.modules.identity.auth.Sessions
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class UserView(
	val id: UUID,
	val username: String,
	val name: String,
	val email: String?,
	val language: String,
	val active: Boolean,
	val locked: Boolean,
	val mustChangePassword: Boolean,
	val roles: List<String>,
	val warehouseIds: List<UUID>,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
)

data class PermissionExceptionView(
	val permission: String,
	val scope: Scope,
	val reason: String,
	val expiresAt: Instant?,
	val grantedBy: UUID,
	val createdAt: Instant,
)

data class CreateUserRequest(val username: String?, val name: String?, val email: String?, val language: String?, val roles: List<String>?)
data class CreatedUser(val user: UserView, val temporaryPassword: String)
data class TemporaryPassword(val temporaryPassword: String)
data class SetRolesRequest(val roles: List<String>?)
data class SetExceptionRequest(val scope: String?, val reason: String?, val expiresAt: String?)
data class SetWarehousesRequest(val warehouseIds: List<UUID>?)

// A person as other modules see them: names for display, and their access to decide what they may be given.
data class Person(val id: UUID, val username: String, val name: String, val active: Boolean, val access: CurrentUser)

@Service
class UserService(
	private val users: UserRepository,
	private val roles: RoleRepository,
	private val exceptions: UserPermissionRepository,
	private val passwords: Passwords,
	private val sessions: Sessions,
	private val audit: AuditService,
	private val warehouseDirectory: WarehouseService,
	private val policies: PolicyGuard,
	private val antiLockout: AntiLockout,
	private val accounts: IdentityAccountLoader,
	private val clock: Clock,
) {
	private val table = TableSpec<User>(
		filters = mapOf(
			"username" to TableSpec.contains("username"),
			"name" to TableSpec.contains("name"),
			"email" to TableSpec.contains("email"),
			"active" to TableSpec.flag("active"),
			"role" to { root, builder, value -> builder.isMember(value.toString(), root.get<Set<String>>("roles")) },
			"lastLoginAt" to TableSpec.between("lastLoginAt"),
		),
		sorts = mapOf("username" to "username", "name" to "name", "email" to "email", "lastLoginAt" to "lastLoginAt", "createdAt" to "createdAt"),
		defaultSort = Sort.by("username"),
	)

	@Transactional(readOnly = true)
	fun query(request: TableRequest): TablePage<UserView> = users.table(request, table, map = ::view)

	@Transactional(readOnly = true)
	fun get(id: UUID): UserView = view(find(id))

	// The temporary password is generated here and returned once. Only its hash is stored.
	@Transactional
	fun create(actor: CurrentUser, request: CreateUserRequest): CreatedUser {
		val validator = Validator()
		val username = validator.required("username", request.username, Rules::username) { it.lowercase() }
		val name = validator.required("name", request.name, Rules::name)
		val email = validator.optional("email", request.email, Rules::email) { it.lowercase() }
		val language = validator.optional("language", request.language, Rules::language) ?: "es"
		val newRoles = request.roles.orEmpty().toSet()
		if (newRoles.any { it !in assignableRoles() }) validator.reject("roles", ErrorCode.CATALOG_VALUE_INVALID)
		validator.check()
		if (users.existsByUsername(username)) throw ValidationException(listOf(FieldError("username", ErrorCode.DUPLICATE_VALUE)))
		// Giving roles is granting access: it needs its own permission, and it passes the same policies.
		if (newRoles.isNotEmpty()) {
			if (!actor.can("users.permissions")) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
			policies.enforce(ACCESS_ACTION, actor, mapOf("target.roles" to emptyList<String>(), "roles" to newRoles.sorted()))
		}

		val temporary = passwords.temporary()
		val now = clock.instant()
		val user = User(username, name, email, language, passwords.hash(temporary), createdBy = actor.id, createdAt = now, updatedAt = now)
		user.roles.addAll(newRoles)
		val saved = users.save(user)
		audit.record(actor.actor, "USER_CREATED", "user", saved.id.toString(), after = snapshot(saved))
		return CreatedUser(view(saved), temporary)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, body: Map<String, Any?>): UserView {
		val patch = Patch(body, setOf("name", "email", "language"))
		val user = find(id)
		val before = snapshot(user)
		val validator = Validator()
		if (patch.has("name")) user.name = validator.required("name", patch.text("name"), Rules::name)
		if (patch.has("email")) user.email = validator.optional("email", patch.text("email"), Rules::email) { it.lowercase() }
		if (patch.has("language")) user.language = validator.required("language", patch.text("language"), Rules::language)
		validator.check()
		user.updatedAt = clock.instant()
		audit.record(actor.actor, "USER_UPDATED", "user", id.toString(), before, snapshot(user))
		return view(user)
	}

	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID): UserView {
		val user = find(id)
		if (user.active) {
			user.active = false
			user.updatedAt = clock.instant()
			sessions.closeAll(id)
			audit.record(actor.actor, "USER_DEACTIVATED", "user", id.toString())
			antiLockout.check()
		}
		return view(user)
	}

	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID): UserView {
		val user = find(id)
		if (!user.active) {
			user.active = true
			user.updatedAt = clock.instant()
			audit.record(actor.actor, "USER_REACTIVATED", "user", id.toString())
		}
		return view(user)
	}

	// A new temporary password, returned once. The person must change it, and their sessions end.
	@Transactional
	fun resetPassword(actor: CurrentUser, id: UUID): TemporaryPassword {
		val user = find(id)
		val temporary = passwords.temporary()
		user.passwordHash = passwords.hash(temporary)
		user.mustChangePassword = true
		user.updatedAt = clock.instant()
		sessions.closeAll(id)
		audit.record(actor.actor, "USER_PASSWORD_RESET", "user", id.toString())
		return TemporaryPassword(temporary)
	}

	@Transactional
	fun unlock(actor: CurrentUser, id: UUID): UserView {
		val user = find(id)
		if (user.locked || user.failedLogins > 0) {
			user.lockedAt = null
			user.failedLogins = 0
			user.updatedAt = clock.instant()
			audit.record(actor.actor, "USER_UNLOCKED", "user", id.toString())
		}
		return view(user)
	}

	@Transactional
	fun setRoles(actor: CurrentUser, id: UUID, request: SetRolesRequest): UserView {
		if (actor.id == id) throw ApiException(ErrorCode.SELF_ROLE_CHANGE)
		val user = find(id)
		val newRoles = request.roles?.toSet() ?: throw ValidationException(listOf(FieldError("roles", ErrorCode.FIELD_REQUIRED)))
		// A role the person already holds can stay even if it was deactivated. A new one must be active.
		if ((newRoles - user.roles).any { it !in assignableRoles() }) throw ValidationException(listOf(FieldError("roles", ErrorCode.CATALOG_VALUE_INVALID)))
		val before = user.roles.sorted()
		policies.enforce(ACCESS_ACTION, actor, mapOf("target.id" to id.toString(), "target.roles" to before, "roles" to newRoles.sorted()))
		user.roles.clear()
		user.roles.addAll(newRoles)
		user.updatedAt = clock.instant()
		audit.record(actor.actor, "USER_ROLES_CHANGED", "user", id.toString(), mapOf("roles" to before), mapOf("roles" to newRoles.sorted()))
		antiLockout.check()
		return view(user)
	}

	@Transactional
	fun setWarehouses(actor: CurrentUser, id: UUID, request: SetWarehousesRequest): UserView {
		val user = find(id)
		val warehouses = request.warehouseIds?.toSet() ?: throw ValidationException(listOf(FieldError("warehouseIds", ErrorCode.FIELD_REQUIRED)))
		warehouseDirectory.requireActive("warehouseIds", warehouses)
		val before = user.warehouseIds.map { it.toString() }.sorted()
		user.warehouseIds.clear()
		user.warehouseIds.addAll(warehouses)
		user.updatedAt = clock.instant()
		audit.record(
			actor.actor, "USER_WAREHOUSES_CHANGED", "user", id.toString(),
			mapOf("warehouseIds" to before), mapOf("warehouseIds" to warehouses.map { it.toString() }.sorted()),
		)
		return view(user)
	}

	// For other modules: who these people are, and what they can do now, resolved as for a request of theirs.
	@Transactional(readOnly = true)
	fun people(ids: Collection<UUID>): Map<UUID, Person> =
		users.findAllById(ids.toSet()).associate { it.id!! to Person(it.id!!, it.username, it.name, it.active, accounts.currentUser(it)) }

	// For other modules: the active people who work in a warehouse, with their access.
	@Transactional(readOnly = true)
	fun peopleInWarehouse(warehouseId: UUID): List<Person> =
		users.findActiveInWarehouse(warehouseId).map { Person(it.id!!, it.username, it.name, it.active, accounts.currentUser(it)) }

	@Transactional(readOnly = true)
	fun exceptions(id: UUID): List<PermissionExceptionView> {
		find(id)
		return exceptions.findByUserId(id).sortedBy { it.permissionKey }.map(::view)
	}

	// An exception replaces what the roles give for one permission, until it expires. It needs a reason.
	@Transactional
	fun setException(actor: CurrentUser, id: UUID, permission: String, request: SetExceptionRequest): PermissionExceptionView {
		if (actor.id == id) throw ApiException(ErrorCode.SELF_ROLE_CHANGE)
		val user = find(id)
		val definition = PermissionCatalog.find(permission) ?: throw ApiException(ErrorCode.NOT_FOUND)
		val validator = Validator()
		val scope = Scope.entries.firstOrNull { it.name == request.scope }
		if (request.scope.isNullOrBlank()) validator.reject("scope", ErrorCode.FIELD_REQUIRED)
		else if (scope == null) validator.reject("scope", ErrorCode.FORMAT_INVALID)
		else if (scope != Scope.NONE && scope !in definition.scopes) validator.reject("scope", ErrorCode.SCOPE_NOT_SUPPORTED)
		val reason = validator.required("reason", request.reason, Rules.text(500))
		val expiresAt = request.expiresAt?.takeIf { it.isNotBlank() }?.let {
			try {
				java.time.OffsetDateTime.parse(it).toInstant().takeIf { instant -> instant.isAfter(clock.instant()) }
			} catch (e: DateTimeParseException) {
				null
			} ?: validator.reject("expiresAt", ErrorCode.FORMAT_INVALID).let { null }
		}
		validator.check()
		policies.enforce(
			ACCESS_ACTION, actor,
			mapOf("target.id" to id.toString(), "target.roles" to user.roles.sorted(), "permission" to permission, "scope" to scope!!.name),
		)

		val existing = exceptions.findByUserIdAndPermissionKey(id, permission)
		val before = existing?.let(::snapshot)
		val saved = (existing ?: UserPermission(id, permission, scope, reason, expiresAt, actor.id, clock.instant())).also {
			it.scope = scope
			it.reason = reason
			it.expiresAt = expiresAt
			it.grantedBy = actor.id
		}.let(exceptions::save)
		audit.record(actor.actor, "PERMISSION_EXCEPTION_SET", "user", id.toString(), before, snapshot(saved))
		antiLockout.check()
		return view(saved)
	}

	@Transactional
	fun removeException(actor: CurrentUser, id: UUID, permission: String) {
		if (actor.id == id) throw ApiException(ErrorCode.SELF_ROLE_CHANGE)
		find(id)
		val existing = exceptions.findByUserIdAndPermissionKey(id, permission) ?: throw ApiException(ErrorCode.NOT_FOUND)
		exceptions.delete(existing)
		audit.record(actor.actor, "PERMISSION_EXCEPTION_REMOVED", "user", id.toString(), before = snapshot(existing))
		antiLockout.check()
	}

	private fun find(id: UUID): User = users.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun assignableRoles(): Set<String> = roles.findAll().filter { it.active }.map { it.key }.toSet()

	private fun view(user: User) = UserView(
		id = user.id!!,
		username = user.username,
		name = user.name,
		email = user.email,
		language = user.language,
		active = user.active,
		locked = user.locked,
		mustChangePassword = user.mustChangePassword,
		roles = user.roles.sorted(),
		warehouseIds = user.warehouseIds.sortedBy { it.toString() },
		lastLoginAt = user.lastLoginAt,
		createdAt = user.createdAt,
	)

	private fun view(exception: UserPermission) = PermissionExceptionView(
		exception.permissionKey, exception.scope, exception.reason, exception.expiresAt, exception.grantedBy, exception.createdAt,
	)

	// What the audit log keeps of a user. Never the password or its hash.
	private fun snapshot(user: User): Map<String, Any?> = mapOf(
		"username" to user.username,
		"name" to user.name,
		"email" to user.email,
		"language" to user.language,
		"roles" to user.roles.sorted(),
	)

	private fun snapshot(exception: UserPermission): Map<String, Any?> = mapOf(
		"permission" to exception.permissionKey,
		"scope" to exception.scope.name,
		"reason" to exception.reason,
		"expiresAt" to exception.expiresAt?.toString(),
	)

	companion object {
		// The policy action for granting roles or exceptions (access-control.md section 6).
		const val ACCESS_ACTION = "users.permissions.set"
	}
}
