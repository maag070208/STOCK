package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.TestcontainersConfiguration
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.modules.catalogs.CatalogSeeder
import com.axzydev.stock.api.modules.catalogs.SeedCatalog
import com.axzydev.stock.api.modules.catalogs.SeedCatalogs
import com.axzydev.stock.api.modules.catalogs.SeedItem
import com.axzydev.stock.api.modules.identity.access.AccessSeeder
import com.axzydev.stock.api.modules.identity.access.PermissionCatalog
import com.axzydev.stock.api.modules.identity.access.PermissionDef
import com.axzydev.stock.api.modules.identity.access.SeedRole
import com.axzydev.stock.api.modules.identity.access.SeedRoles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

// F1-01 and F1-16 · the seeds are migrations that can run any number of times.
// They add what is missing, and they never change what an administrator may have changed.
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AccessSeedTest {

	@Autowired
	lateinit var dataSource: DataSource

	@Autowired
	lateinit var jdbc: JdbcTemplate

	private fun count(table: String): Int = jdbc.queryForObject("select count(*) from $table", Int::class.java)!!

	private fun seedAccess(roles: List<SeedRole>, permissions: List<PermissionDef>) =
		dataSource.connection.use { AccessSeeder(it).seed(roles, permissions) }

	@Test
	fun `the migration seeded the catalog and the roles of the code`() {
		val keys = jdbc.queryForList("select key from permissions order by sort_order", String::class.java)
		assertEquals(PermissionCatalog.all.map { it.key }, keys)
		val roles = jdbc.queryForList("select key from roles where system order by sort_order", String::class.java)
		assertEquals(SeedRoles.all.map { it.key }, roles)
	}

	@Test
	fun `running the access seed again writes nothing new`() {
		val before = listOf("permissions", "roles", "role_permissions").associateWith(::count)
		seedAccess(SeedRoles.all, PermissionCatalog.all)
		seedAccess(SeedRoles.all, PermissionCatalog.all)
		assertEquals(before, listOf("permissions", "roles", "role_permissions").associateWith(::count))
	}

	@Test
	fun `a new permission gets its seed grants once, and a cell an administrator removed is not put back`() {
		val role = SeedRole("SEED_TEST_ROLE", 90)
		val permission = PermissionDef("seedtest.view", listOf(Scope.ALL), grants = mapOf(role.key to Scope.ALL))
		val cells = { jdbc.queryForObject("select count(*) from role_permissions where permission_key = 'seedtest.view'", Int::class.java) }
		try {
			seedAccess(listOf(role), listOf(permission))
			assertEquals(1, cells())
			// An administrator takes the permission away from the role.
			jdbc.update("delete from role_permissions where permission_key = 'seedtest.view'")
			seedAccess(listOf(role), listOf(permission))
			assertEquals(0, cells())
		} finally {
			jdbc.update("delete from role_permissions where permission_key = 'seedtest.view'")
			jdbc.update("delete from permissions where key = 'seedtest.view'")
			jdbc.update("delete from roles where key = 'SEED_TEST_ROLE'")
		}
	}

	@Test
	fun `the catalog seed adds missing values and never changes existing ones`() {
		val seedTwice = { catalogs: List<SeedCatalog> -> dataSource.connection.use { CatalogSeeder(it).seed(catalogs); CatalogSeeder(it).seed(catalogs) } }
		val before = listOf("catalog", "catalog_item", "catalog_item_label").associateWith(::count)
		seedTwice(SeedCatalogs.all)
		assertEquals(before, listOf("catalog", "catalog_item", "catalog_item_label").associateWith(::count))

		// The same code with another label: the label in the database stays. A value that is missing is added.
		val changed = listOf(SeedCatalog("UNIT_OF_MEASURE", listOf(SeedItem("PIECE", "Otra etiqueta", "Another label"), SeedItem("SEEDTEST", "Prueba", "Test"))))
		try {
			seedTwice(changed)
			val label = jdbc.queryForObject(
				"select l.label from catalog_item_label l join catalog_item i on i.id = l.catalog_item_id where i.code = 'PIECE' and l.locale = 'es'",
				String::class.java,
			)
			assertEquals("Pieza", label)
			assertEquals(1, jdbc.queryForObject("select count(*) from catalog_item where code = 'SEEDTEST'", Int::class.java))
		} finally {
			jdbc.update("delete from catalog_item_label where catalog_item_id in (select id from catalog_item where code = 'SEEDTEST')")
			jdbc.update("delete from catalog_item where code = 'SEEDTEST'")
		}
	}
}
