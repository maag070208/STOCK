package com.axzydev.stock.api.modules.identity.access

import java.sql.Connection

// Writes the seed roles and the permission catalog. Safe to run any number of times:
// - a role or a permission that exists is not added again;
// - a permission's own data (module, scopes, sensitive) follows the code;
// - the seed grants of a permission are written only when the permission is first added.
//   After that the matrix belongs to the administrators, and a cell they removed is not put back.
class AccessSeeder(private val connection: Connection) {

	fun seed(roles: List<SeedRole>, permissions: List<PermissionDef>) {
		connection.prepareStatement(
			"insert into roles (key, system, sort_order, created_at, updated_at) values (?, true, ?, now(), now()) on conflict (key) do nothing",
		).use { insert ->
			for (role in roles) {
				insert.setString(1, role.key)
				insert.setInt(2, role.sortOrder)
				insert.executeUpdate()
			}
		}
		permissions.forEachIndexed { index, permission ->
			if (upsert(permission, sortOrder = index + 1)) grant(permission)
		}
	}

	// True when the permission is new.
	private fun upsert(permission: PermissionDef, sortOrder: Int): Boolean =
		connection.prepareStatement(
			"""
			insert into permissions (key, module, name_key, scopes, sensitive, sort_order) values (?, ?, ?, ?, ?, ?)
			on conflict (key) do update set module = excluded.module, name_key = excluded.name_key,
				scopes = excluded.scopes, sensitive = excluded.sensitive, sort_order = excluded.sort_order
			returning (xmax = 0) as inserted
			""".trimIndent(),
		).use { statement ->
			statement.setString(1, permission.key)
			statement.setString(2, permission.module)
			statement.setString(3, permission.nameKey)
			statement.setArray(4, connection.createArrayOf("varchar", permission.scopes.map { it.name }.toTypedArray()))
			statement.setBoolean(5, permission.sensitive)
			statement.setInt(6, sortOrder)
			statement.executeQuery().use { it.next() && it.getBoolean("inserted") }
		}

	private fun grant(permission: PermissionDef) {
		connection.prepareStatement(
			"insert into role_permissions (role_key, permission_key, scope) values (?, ?, ?) on conflict do nothing",
		).use { insert ->
			for ((role, scope) in permission.grants) {
				insert.setString(1, role)
				insert.setString(2, permission.key)
				insert.setString(3, scope.name)
				insert.executeUpdate()
			}
		}
	}
}
