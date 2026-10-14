package com.axzydev.stock.api.modules.identity.access

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, String>

interface RoleRepository : JpaRepository<Role, String>

interface RolePermissionRepository : JpaRepository<RolePermission, RolePermissionId>

interface UserPermissionRepository : JpaRepository<UserPermission, UUID> {
	fun findByUserId(userId: UUID): List<UserPermission>

	fun findByUserIdAndPermissionKey(userId: UUID, permissionKey: String): UserPermission?
}

interface PolicyRepository : JpaRepository<PolicyRow, String> {

	@Modifying
	@Query(value = "delete from policy_roles where role_key = :roleKey", nativeQuery = true)
	fun removeRole(roleKey: String)
}
