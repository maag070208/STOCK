package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.security.Scope

// A permission guards code, so developers define it here (access-control.md section 4).
// `grants` is what each seed role receives when the permission is first added. After that, the matrix is data.
data class PermissionDef(
	val key: String,
	val scopes: List<Scope>,
	val sensitive: Boolean = false,
	val grants: Map<String, Scope> = emptyMap(),
) {
	val module: String get() = key.substringBefore(".")
	val nameKey: String get() = "permissions.$key"
}

data class SeedRole(val key: String, val sortOrder: Int)

object SeedRoles {
	const val ADMIN = "ADMIN"
	const val OFFICE = "OFFICE"
	const val WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER"
	const val WAREHOUSE_OPERATOR = "WAREHOUSE_OPERATOR"
	const val APP_USER = "APP_USER"

	val all = listOf(
		SeedRole(ADMIN, 1),
		SeedRole(OFFICE, 2),
		SeedRole(WAREHOUSE_MANAGER, 3),
		SeedRole(WAREHOUSE_OPERATOR, 4),
		SeedRole(APP_USER, 5),
	)
}

// The permissions that exist so far. Each phase adds the ones of its modules; ADMIN receives every one of them.
object PermissionCatalog {

	const val ROLES_MANAGE = "roles.manage"

	private val ALL = listOf(Scope.ALL)

	private fun adminOnly(key: String, sensitive: Boolean = false) =
		PermissionDef(key, ALL, sensitive, mapOf(SeedRoles.ADMIN to Scope.ALL))

	val all: List<PermissionDef> = listOf(
		adminOnly("users.view"),
		adminOnly("users.create"),
		adminOnly("users.edit"),
		adminOnly("users.delete"),
		adminOnly("users.permissions", sensitive = true),
		adminOnly(ROLES_MANAGE, sensitive = true),
		adminOnly("audit.view"),
		adminOnly("config.view"),
		adminOnly("config.manage"),
		PermissionDef(
			"catalogs.view", ALL,
			grants = mapOf(
				SeedRoles.ADMIN to Scope.ALL,
				SeedRoles.OFFICE to Scope.ALL,
				SeedRoles.WAREHOUSE_MANAGER to Scope.ALL,
				SeedRoles.WAREHOUSE_OPERATOR to Scope.ALL,
			),
		),
		adminOnly("catalogs.manage"),
		PermissionDef(
			"web.access", ALL,
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.ALL),
		),
		PermissionDef(
			"app.access", ALL,
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.WAREHOUSE_OPERATOR to Scope.ALL, SeedRoles.APP_USER to Scope.ALL),
		),
		// F2: warehouses and locations. AREA reaches the warehouses assigned to the person.
		PermissionDef(
			"locations.view", listOf(Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.AREA, SeedRoles.WAREHOUSE_OPERATOR to Scope.AREA),
		),
		adminOnly("locations.manage"),
		// F2: materials. Everyone who works with stock reads them; only ADMIN changes them (products.md section 7).
		PermissionDef(
			"products.view", ALL,
			grants = mapOf(
				SeedRoles.ADMIN to Scope.ALL,
				SeedRoles.OFFICE to Scope.ALL,
				SeedRoles.WAREHOUSE_MANAGER to Scope.ALL,
				SeedRoles.WAREHOUSE_OPERATOR to Scope.ALL,
			),
		),
		adminOnly("products.manage"),
		// F2: suppliers and customers, with their contacts. OFFICE and WAREHOUSE_MANAGER read them; only ADMIN changes them.
		PermissionDef(
			"suppliers.view", ALL,
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.ALL),
		),
		adminOnly("suppliers.manage"),
		PermissionDef(
			"customers.view", ALL,
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.ALL),
		),
		adminOnly("customers.manage"),
		// F4: stock. Who else reads it is not decided (access-control.md section 4): until then, ADMIN, and the matrix.
		PermissionDef("stock.view", listOf(Scope.AREA, Scope.ALL), grants = mapOf(SeedRoles.ADMIN to Scope.ALL)),
		// Loads the legacy file's stock into a warehouse, once (products.md section 5).
		adminOnly("stock.opening"),
		// F4: material entry and receiving (access-control.md section 4, roles.md section 3).
		PermissionDef(
			"invoices.view", listOf(Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.AREA),
		),
		PermissionDef("invoices.create", ALL, grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL)),
		adminOnly("invoices.manage", sensitive = true),
		PermissionDef(
			"receipts.view", listOf(Scope.OWN, Scope.AREA, Scope.ALL),
			grants = mapOf(
				SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL,
				SeedRoles.WAREHOUSE_MANAGER to Scope.AREA, SeedRoles.WAREHOUSE_OPERATOR to Scope.OWN,
			),
		),
		PermissionDef(
			"receipts.assign", listOf(Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.AREA),
		),
		PermissionDef(
			"receipts.receive", listOf(Scope.OWN, Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.WAREHOUSE_OPERATOR to Scope.OWN),
		),
		PermissionDef(
			"receipts.place", listOf(Scope.OWN, Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.WAREHOUSE_OPERATOR to Scope.OWN),
		),
		PermissionDef(
			"discrepancies.resolve", listOf(Scope.AREA, Scope.ALL), sensitive = true,
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.AREA),
		),
		PermissionDef(
			"staff_templates.manage", listOf(Scope.AREA, Scope.ALL),
			grants = mapOf(SeedRoles.ADMIN to Scope.ALL, SeedRoles.WAREHOUSE_MANAGER to Scope.AREA),
		),
		// F3: label templates and printers (labels.md section 7, access-control.md section 4). Templates and printers reach
		// the printers' network, so only ADMIN reads or changes them. Everyone prints except APP_USER.
		adminOnly("labels.view", sensitive = true),
		adminOnly("labels.manage", sensitive = true),
		PermissionDef(
			"labels.print", listOf(Scope.AREA, Scope.ALL),
			grants = mapOf(
				SeedRoles.ADMIN to Scope.ALL, SeedRoles.OFFICE to Scope.ALL,
				SeedRoles.WAREHOUSE_MANAGER to Scope.AREA, SeedRoles.WAREHOUSE_OPERATOR to Scope.AREA,
			),
		),
		adminOnly("printers.view", sensitive = true),
		adminOnly("printers.manage", sensitive = true),
	)

	val keys: Set<String> = all.map { it.key }.toSet()

	fun find(key: String): PermissionDef? = all.firstOrNull { it.key == key }
}
