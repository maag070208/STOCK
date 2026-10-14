package com.axzydev.stock.api.modules.identity.users

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
	var username: String,
	var name: String,
	var email: String?,
	var language: String,
	var passwordHash: String,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	// True until the person sets a password of their own.
	var mustChangePassword: Boolean = true
	var active: Boolean = true
	var failedLogins: Int = 0
	var lockedAt: Instant? = null
	var lastLoginAt: Instant? = null

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
	@Column(name = "role_key")
	var roles: MutableSet<String> = mutableSetOf()

	// The warehouses of the user's AREA scope.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_warehouses", joinColumns = [JoinColumn(name = "user_id")])
	@Column(name = "warehouse_id")
	var warehouseIds: MutableSet<UUID> = mutableSetOf()

	val locked: Boolean get() = lockedAt != null
}

interface UserRepository : JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

	fun findByUsername(username: String): User?

	// The active people who work in a warehouse (its users' AREA).
	@Query("select u from User u join u.warehouseIds w where w = :warehouseId and u.active = true")
	fun findActiveInWarehouse(warehouseId: UUID): List<User>

	fun existsByUsername(username: String): Boolean

	@Query(value = "select count(*) from user_roles where role_key = :roleKey", nativeQuery = true)
	fun countByRole(roleKey: String): Long

	// The people who can still manage access: active, not locked, holding roles.manage through an active role,
	// and without a valid exception that takes it away. The anti-lockout guard needs at least one.
	@Query(
		value = """
			select count(*) from users u
			where u.active and u.locked_at is null
			and exists (
				select 1 from user_roles ur
				join roles r on r.key = ur.role_key and r.active
				join role_permissions rp on rp.role_key = r.key and rp.permission_key = 'roles.manage'
				where ur.user_id = u.id
			)
			and not exists (
				select 1 from user_permissions up
				where up.user_id = u.id and up.permission_key = 'roles.manage' and up.scope = 'NONE'
				and (up.expires_at is null or up.expires_at > :now)
			)
		""",
		nativeQuery = true,
	)
	fun countAccessManagers(now: Instant): Long
}
