package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.core.security.Channel
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.modules.identity.access.PermissionCatalog
import com.axzydev.stock.api.modules.identity.access.SeedRoles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F1-01, F1-07 and F1-08 · unit: the permission catalog and the seed matrix defined in the code.
class PermissionCatalogTest {

	@Test
	fun `every permission key is module dot action in lowercase, and no key repeats`() {
		val format = Regex("^[a-z_]+(\\.[a-z_]+)+$")
		PermissionCatalog.all.forEach { assertTrue(format.matches(it.key), it.key) }
		assertEquals(PermissionCatalog.all.size, PermissionCatalog.keys.size)
	}

	@Test
	fun `ADMIN holds every permission at its highest scope`() {
		PermissionCatalog.all.forEach { assertEquals(it.scopes.max(), it.grants[SeedRoles.ADMIN], it.key) }
	}

	@Test
	fun `a seed grant is always a scope the permission supports, for a seed role`() {
		val roles = SeedRoles.all.map { it.key }.toSet()
		PermissionCatalog.all.forEach { permission ->
			permission.grants.forEach { (role, scope) ->
				assertTrue(role in roles, "${permission.key} names the unknown role $role")
				assertTrue(scope in permission.scopes, "${permission.key} grants $scope to $role")
			}
		}
	}

	// docs/rules/roles.md section 3, for the permissions that exist so far. Each row is one line of the table.
	@Test
	fun `the seed matrix matches roles md, cell by cell`() {
		val all = Scope.ALL
		val area = Scope.AREA
		val own = Scope.OWN
		val none: Scope? = null
		//                                  ADMIN OFFICE MANAGER OPERATOR APP_USER
		val expected = mapOf(
			"users.view" to listOf(all, none, none, none, none),
			"users.create" to listOf(all, none, none, none, none),
			"users.edit" to listOf(all, none, none, none, none),
			"users.delete" to listOf(all, none, none, none, none),
			"users.permissions" to listOf(all, none, none, none, none),
			"roles.manage" to listOf(all, none, none, none, none),
			"audit.view" to listOf(all, none, none, none, none),
			"config.view" to listOf(all, none, none, none, none),
			"config.manage" to listOf(all, none, none, none, none),
			"catalogs.view" to listOf(all, all, all, all, none),
			"catalogs.manage" to listOf(all, none, none, none, none),
			"web.access" to listOf(all, all, all, none, none),
			"app.access" to listOf(all, none, none, all, all),
			"locations.view" to listOf(all, none, area, area, none),
			"locations.manage" to listOf(all, none, none, none, none),
			"products.view" to listOf(all, all, all, all, none),
			"products.manage" to listOf(all, none, none, none, none),
			"suppliers.view" to listOf(all, all, all, none, none),
			"suppliers.manage" to listOf(all, none, none, none, none),
			"customers.view" to listOf(all, all, all, none, none),
			"customers.manage" to listOf(all, none, none, none, none),
			"stock.view" to listOf(all, none, none, none, none),
			"stock.opening" to listOf(all, none, none, none, none),
			"invoices.view" to listOf(all, all, area, none, none),
			"invoices.create" to listOf(all, all, none, none, none),
			"invoices.manage" to listOf(all, none, none, none, none),
			"receipts.view" to listOf(all, all, area, own, none),
			"receipts.assign" to listOf(all, all, area, none, none),
			"receipts.receive" to listOf(all, none, none, own, none),
			"receipts.place" to listOf(all, none, none, own, none),
			"discrepancies.resolve" to listOf(all, all, area, none, none),
			"staff_templates.manage" to listOf(all, none, area, none, none),
			"labels.view" to listOf(all, none, none, none, none),
			"labels.manage" to listOf(all, none, none, none, none),
			"labels.print" to listOf(all, all, area, area, none),
			"printers.view" to listOf(all, none, none, none, none),
			"printers.manage" to listOf(all, none, none, none, none),
			"orders.create" to listOf(all, all, none, none, none),
			"orders.view" to listOf(all, all, area, area, none),
			"orders.assign" to listOf(all, all, area, none, none),
			"orders.fulfill" to listOf(all, none, none, area, none),
			"orders.validate" to listOf(all, all, area, none, none),
			"orders.cancel" to listOf(all, all, area, none, none),
			"orders.reassign" to listOf(all, all, area, none, none),
			"orders.accept_difference" to listOf(all, none, area, none, none),
		)
		val roles = listOf(SeedRoles.ADMIN, SeedRoles.OFFICE, SeedRoles.WAREHOUSE_MANAGER, SeedRoles.WAREHOUSE_OPERATOR, SeedRoles.APP_USER)
		assertEquals(expected.keys, PermissionCatalog.keys)
		for ((permission, cells) in expected) {
			val definition = PermissionCatalog.find(permission)!!
			roles.forEachIndexed { index, role -> assertEquals(cells[index], definition.grants[role], "$permission for $role") }
		}
	}

	@Test
	fun `the sensitive permissions are the ones that change access`() {
		assertEquals(
			setOf(
				"users.permissions", "roles.manage", "invoices.manage", "discrepancies.resolve",
				"labels.view", "labels.manage", "printers.view", "printers.manage", "orders.accept_difference",
			),
			PermissionCatalog.all.filter { it.sensitive }.map { it.key }.toSet(),
		)
	}

	@Test
	fun `a request names its channel as web or app, and each channel has its own permission`() {
		assertEquals(Channel.WEB, Channel.of("web"))
		assertEquals(Channel.APP, Channel.of("app"))
		assertNull(Channel.of("desktop"))
		assertNull(Channel.of(null))
		assertEquals("web.access", Channel.WEB.permission)
		assertEquals("app.access", Channel.APP.permission)
		assertTrue(PermissionCatalog.keys.containsAll(Channel.entries.map { it.permission }))
	}
}
