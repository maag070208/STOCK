package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.security.Scope
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant
import java.util.UUID

// The catalog row of a permission. It is written by the seed only.
@Entity
@Table(name = "permissions")
class Permission(
	@Id var key: String,
	var module: String,
	var nameKey: String,
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "varchar(4)[]")
	var scopes: List<String>,
	var sensitive: Boolean,
	var active: Boolean,
	var sortOrder: Int,
)

@Entity
@Table(name = "roles")
class Role(
	@Id var key: String,
	// Seed roles have no stored name: the apps translate their key. A custom role stores its name as typed.
	var name: String?,
	var system: Boolean,
	var active: Boolean,
	var sortOrder: Int,
	var createdAt: Instant,
	var updatedAt: Instant,
)

@Embeddable
data class RolePermissionId(
	@Column(name = "role_key") var roleKey: String = "",
	@Column(name = "permission_key") var permissionKey: String = "",
) : Serializable

// One cell of the matrix. A missing cell means NONE.
@Entity
@Table(name = "role_permissions")
class RolePermission(
	@EmbeddedId var id: RolePermissionId,
	@Enumerated(EnumType.STRING) var scope: Scope,
)

// An exception for one person, with the reason and who granted it.
@Entity
@Table(name = "user_permissions")
class UserPermission(
	var userId: UUID,
	var permissionKey: String,
	@Enumerated(EnumType.STRING) var scope: Scope,
	var reason: String,
	var expiresAt: Instant?,
	var grantedBy: UUID,
	var createdAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

@Embeddable
class PolicyConditionRow(
	var field: String = "",
	var operator: String = "",
	// JSON text, stored as jsonb.
	@Column(columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	var value: String = "null",
)

@Entity
@Table(name = "policies")
class PolicyRow(
	@Id var key: String,
	var priority: Int,
	var effect: String,
	var action: String,
	var active: Boolean,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "policy_conditions", joinColumns = [JoinColumn(name = "policy_key")])
	var conditions: MutableList<PolicyConditionRow> = mutableListOf()

	// No roles means the policy applies to everyone.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "policy_roles", joinColumns = [JoinColumn(name = "policy_key")])
	@Column(name = "role_key")
	var roles: MutableSet<String> = mutableSetOf()
}
