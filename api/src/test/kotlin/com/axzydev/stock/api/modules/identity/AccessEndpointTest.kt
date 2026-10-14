package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestClock
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID

// F1-01 and F1-08 to F1-13 · e2e: the permission catalog, the matrix, multi-role, exceptions, warehouses and policies.
class AccessEndpointTest : ApiTest() {

	@Autowired
	lateinit var clock: TestClock

	private fun roleKey() = "T_" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

	private fun matrix(role: String, permission: String, scope: String) =
		api.put("/roles/matrix", api.adminToken(), mapOf("changes" to listOf(mapOf("roleKey" to role, "permissionKey" to permission, "scope" to scope))))

	@Test
	fun `an administrator lists the seeded permission catalog, and an app user is refused`() {
		val catalog = api.get("/permissions", api.adminToken())
		assertEquals(200, catalog.status)
		val keys = catalog.json.list.map { it.get("key").asString() }
		assertEquals(listOf("users.view", "users.create", "users.edit", "users.delete", "users.permissions", "roles.manage"), keys.take(6))
		val manage = catalog.json.list.first { it.get("key").asString() == "roles.manage" }
		assertTrue(manage.get("sensitive").asBoolean())
		assertEquals(listOf("ALL"), manage.get("scopes").list.map { it.asString() })
		assertEquals("permissions.roles.manage", manage.get("nameKey").asString())

		val appUser = api.newUser("APP_USER", channel = "app")
		val refused = api.get("/permissions", appUser.token, channel = "app")
		assertEquals(403, refused.status)
		assertEquals("INSUFFICIENT_PERMISSIONS", refused.code)
	}

	@Test
	fun `a change to the matrix takes effect on the next request, without signing in again`() {
		val role = roleKey()
		assertEquals(201, api.post("/roles", api.adminToken(), mapOf("key" to role, "name" to "Rol de prueba")).status)
		matrix(role, "web.access", "ALL")
		val user = api.newUser(role)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/catalogs", user.token).code)

		assertEquals(200, matrix(role, "catalogs.view", "ALL").status)
		assertEquals(200, api.get("/catalogs", user.token).status)
		assertEquals("ALL", api.get("/auth/me", user.token).json.get("user").get("permissions").get("catalogs.view").asString())

		assertEquals(200, matrix(role, "catalogs.view", "NONE").status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/catalogs", user.token).code)

		val audit = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "PERMISSION_MATRIX_CHANGED", "targetId" to role)))
		assertEquals(3, audit.json.get("total").asInt())
	}

	@Test
	fun `the matrix refuses a scope the permission does not support, and an unknown role or permission`() {
		val unsupported = matrix("OFFICE", "users.view", "AREA")
		assertEquals(400, unsupported.status)
		assertEquals("SCOPE_NOT_SUPPORTED", unsupported.code)
		assertEquals("changes[0].scope", unsupported.json.get("details").get(0).get("field").asString())
		assertEquals("CATALOG_VALUE_INVALID", matrix("NO_SUCH_ROLE", "users.view", "ALL").code)
		assertEquals("CATALOG_VALUE_INVALID", matrix("OFFICE", "no.such_permission", "ALL").code)
		assertEquals("FORMAT_INVALID", matrix("OFFICE", "users.view", "EVERYTHING").code)
	}

	@Test
	fun `a user with two roles gets the union of both`() {
		val both = api.newUser("OFFICE", "WAREHOUSE_OPERATOR")
		val me = api.get("/auth/me", both.token).json.get("user")
		assertEquals(listOf("OFFICE", "WAREHOUSE_OPERATOR"), me.get("roles").list.map { it.asString() })
		// The union of what each role is seeded with, and for a permission both hold, the higher scope.
		val seeded = com.axzydev.stock.api.modules.identity.access.PermissionCatalog.all
			.filter { it.grants.keys.any { role -> role == "OFFICE" || role == "WAREHOUSE_OPERATOR" } }.map { it.key }.toSet()
		assertEquals(seeded, me.get("permissions").propertyNames().toSet())
		assertEquals("AREA", me.get("permissions").get("locations.view").asString())
		// receipts.view: OFFICE has ALL, WAREHOUSE_OPERATOR has OWN.
		assertEquals("ALL", me.get("permissions").get("receipts.view").asString())
		// OFFICE opens the web and WAREHOUSE_OPERATOR the app.
		assertEquals(200, api.get("/catalogs", both.token, channel = "web").status)
		assertEquals(200, api.get("/catalogs", both.token, channel = "app").status)
	}

	@Test
	fun `an exception grants access until it expires, with its reason and who granted it`() {
		val user = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/users/query", user.token, mapOf<String, Any>()).code)

		val expires = Instant.now().plus(Duration.ofHours(1)).toString()
		val granted = api.put("/users/${user.id}/permissions/users.view", api.adminToken(), mapOf("scope" to "ALL", "reason" to "Cubre vacaciones", "expiresAt" to expires))
		assertEquals(200, granted.status)
		assertEquals("Cubre vacaciones", granted.text("reason"))
		assertTrue(granted.text("grantedBy").isNotBlank())
		assertEquals(200, api.post("/users/query", user.token, mapOf<String, Any>()).status)
		assertEquals(1, api.get("/users/${user.id}/permissions", api.adminToken()).json.list.size)

		// Two hours later the exception has expired and grants nothing. The person signs in again, because their token expired too.
		clock.after(Duration.ofHours(2)) {
			val later = api.login(user.username, user.password)
			assertEquals(200, later.status)
			assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/users/query", later.text("token"), mapOf<String, Any>()).code)
		}
	}

	@Test
	fun `an exception can take access away, and removing it gives the role's access back`() {
		val user = api.newUser("OFFICE")
		assertEquals(200, api.get("/catalogs", user.token).status)
		assertEquals(200, api.put("/users/${user.id}/permissions/catalogs.view", api.adminToken(), mapOf("scope" to "NONE", "reason" to "Suspendido")).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/catalogs", user.token).code)
		assertEquals(204, api.delete("/users/${user.id}/permissions/catalogs.view", api.adminToken()).status)
		assertEquals(200, api.get("/catalogs", user.token).status)
	}

	@Test
	fun `an exception needs a reason, a scope the permission supports and an expiry in the future`() {
		val user = api.newUser("OFFICE")
		val set = { body: Map<String, Any?> -> api.put("/users/${user.id}/permissions/users.view", api.adminToken(), body) }
		assertEquals("FIELD_REQUIRED", set(mapOf("scope" to "ALL")).code)
		assertEquals("SCOPE_NOT_SUPPORTED", set(mapOf("scope" to "AREA", "reason" to "x")).code)
		assertEquals("FORMAT_INVALID", set(mapOf("scope" to "ALL", "reason" to "x", "expiresAt" to Instant.now().minusSeconds(60).toString())).code)
		assertEquals("FORMAT_INVALID", set(mapOf("scope" to "ALL", "reason" to "x", "expiresAt" to "tomorrow")).code)
		assertEquals(404, api.put("/users/${user.id}/permissions/no.such_permission", api.adminToken(), mapOf("scope" to "ALL", "reason" to "x")).status)
	}

	@Test
	fun `an administrator assigns the warehouses of a user's area`() {
		val user = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		val central = api.createWarehouse()
		val north = api.createWarehouse()
		val assigned = api.put("/users/${user.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(central, north)))
		assertEquals(200, assigned.status)
		assertEquals(setOf(central, north), assigned.json.get("warehouseIds").list.map { it.asString() }.toSet())
		assertEquals(setOf(central, north), api.get("/users/${user.id}", api.adminToken()).json.get("warehouseIds").list.map { it.asString() }.toSet())
		assertEquals(listOf(north), api.put("/users/${user.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(north))).json.get("warehouseIds").list.map { it.asString() })
		assertEquals("FIELD_REQUIRED", api.put("/users/${user.id}/warehouses", api.adminToken(), mapOf<String, Any>()).code)

		val audit = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "USER_WAREHOUSES_CHANGED", "targetId" to user.id)))
		assertEquals(2, audit.json.get("total").asInt())
	}

	@Test
	fun `a DENY policy refuses the action with POLICY_DENIED, and the audit log names the policy`() {
		val key = "P_" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
		val target = api.newUser("OFFICE")
		val policy = mapOf(
			"key" to key, "priority" to 10, "effect" to "DENY", "action" to "users.permissions.set",
			"conditions" to listOf(mapOf("field" to "permission", "operator" to "eq", "value" to "audit.view")),
		)
		try {
			assertEquals(201, api.post("/policies", api.adminToken(), policy).status)

			// The change applies on the next request: the same token is refused now.
			val denied = api.put("/users/${target.id}/permissions/audit.view", api.adminToken(), mapOf("scope" to "ALL", "reason" to "Revisión"))
			assertEquals(403, denied.status)
			assertEquals("POLICY_DENIED", denied.code)
			assertEquals(0, api.get("/users/${target.id}/permissions", api.adminToken()).json.list.size)
			// Another permission does not match the condition.
			assertEquals(200, api.put("/users/${target.id}/permissions/users.view", api.adminToken(), mapOf("scope" to "ALL", "reason" to "Revisión")).status)

			val audit = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "ACCESS_DENIED", "targetId" to "users.permissions.set")))
			assertTrue(audit.json.get("data").list.any { it.get("after").get("policy").asString() == key }, audit.body)

			// Deactivated, the policy stops applying.
			assertEquals(200, api.put("/policies/$key", api.adminToken(), policy + mapOf("active" to false)).status)
			assertEquals(200, api.put("/users/${target.id}/permissions/audit.view", api.adminToken(), mapOf("scope" to "ALL", "reason" to "Revisión")).status)
		} finally {
			api.delete("/policies/$key", api.adminToken())
		}
	}

	@Test
	fun `a policy applies only to its roles`() {
		val key = "P_" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
		val target = api.newUser("OFFICE")
		val policy = mapOf("key" to key, "priority" to 10, "effect" to "DENY", "action" to "users.permissions.set", "roles" to listOf("OFFICE"))
		try {
			assertEquals(201, api.post("/policies", api.adminToken(), policy).status)
			// The administrator is not an OFFICE user, so the policy does not reach them.
			assertEquals(200, api.put("/users/${target.id}/roles", api.adminToken(), mapOf("roles" to listOf("OFFICE", "APP_USER"))).status)
		} finally {
			api.delete("/policies/$key", api.adminToken())
		}
	}

	@Test
	fun `a policy with an unregistered action or field is rejected, and so is one on roles manage`() {
		val create = { body: Map<String, Any?> -> api.post("/policies", api.adminToken(), mapOf("key" to "P_INVALID", "priority" to 1, "effect" to "DENY") + body) }
		val unknownAction = create(mapOf("action" to "orders.vanish"))
		assertEquals(400, unknownAction.status)
		assertEquals("POLICY_ACTION_UNKNOWN", unknownAction.code)
		assertEquals("POLICY_ACTION_UNKNOWN", create(mapOf("action" to "roles.manage")).code)
		val unknownField = create(mapOf("action" to "users.permissions.set", "conditions" to listOf(mapOf("field" to "target.passwordHash", "operator" to "exists"))))
		assertEquals("POLICY_FIELD_UNKNOWN", unknownField.code)
		assertEquals("conditions[0].field", unknownField.json.get("details").get(0).get("field").asString())
		assertEquals("FORMAT_INVALID", create(mapOf("action" to "users.permissions.set", "conditions" to listOf(mapOf("field" to "scope", "operator" to "between", "value" to 1)))).code)
		assertEquals(0, api.get("/policies", api.adminToken()).json.list.count { it.get("key").asString() == "P_INVALID" })
	}

	@Test
	fun `only who manages roles sees policies, and the registry lists each action with its fields`() {
		val office = api.newUser("OFFICE")
		assertEquals(403, api.get("/policies", office.token).status)
		assertEquals(403, api.post("/policies", office.token, mapOf("key" to "P_X")).status)
		assertEquals(403, api.get("/roles/matrix", office.token).status)

		val actions = api.get("/policies/actions", api.adminToken())
		val set = actions.json.list.first { it.get("key").asString() == "users.permissions.set" }
		assertEquals(setOf("target.id", "target.roles", "roles", "permission", "scope"), set.get("fields").list.map { it.asString() }.toSet())
		assertFalse(actions.json.list.any { it.get("key").asString() == "roles.manage" })
	}

	@Test
	fun `a custom role can be renamed, deactivated and deleted, and a system role cannot`() {
		val role = roleKey()
		assertEquals(201, api.post("/roles", api.adminToken(), mapOf("key" to role.lowercase(), "name" to "Rol de prueba")).status)
		assertEquals("DUPLICATE_VALUE", api.post("/roles", api.adminToken(), mapOf("key" to role, "name" to "Otra vez")).code)
		assertEquals("FORMAT_INVALID", api.post("/roles", api.adminToken(), mapOf("key" to "con espacios", "name" to "Mal")).code)
		assertEquals("Supervisión", api.patch("/roles/$role", api.adminToken(), mapOf("name" to "Supervisión")).text("name"))

		val member = api.newUser(role, "OFFICE")
		val inUse = api.delete("/roles/$role", api.adminToken())
		assertEquals(409, inUse.status)
		assertEquals("ROLE_IN_USE", inUse.code)
		assertEquals(200, api.put("/users/${member.id}/roles", api.adminToken(), mapOf("roles" to listOf("OFFICE"))).status)
		assertEquals(204, api.delete("/roles/$role", api.adminToken()).status)
		assertEquals(404, api.patch("/roles/$role", api.adminToken(), mapOf("name" to "Ya no existe")).status)

		for (change in listOf(mapOf("name" to "Jefes"), mapOf("active" to false))) {
			val refused = api.patch("/roles/ADMIN", api.adminToken(), change)
			assertEquals(409, refused.status)
			assertEquals("SYSTEM_ROLE_PROTECTED", refused.code)
		}
		assertEquals("SYSTEM_ROLE_PROTECTED", api.delete("/roles/APP_USER", api.adminToken()).code)
		val roles = api.get("/roles", api.adminToken()).json.list
		assertEquals(listOf("ADMIN", "OFFICE", "WAREHOUSE_MANAGER", "WAREHOUSE_OPERATOR", "APP_USER"), roles.filter { it.get("system").asBoolean() }.map { it.get("key").asString() })
	}

	@Test
	fun `nobody changes their own roles or permissions`() {
		val admin = api.newUser("ADMIN")
		val ownRoles = api.put("/users/${admin.id}/roles", admin.token, mapOf("roles" to listOf("ADMIN", "OFFICE")))
		assertEquals(403, ownRoles.status)
		assertEquals("SELF_ROLE_CHANGE", ownRoles.code)
		assertEquals("SELF_ROLE_CHANGE", api.put("/users/${admin.id}/permissions/users.view", admin.token, mapOf("scope" to "ALL", "reason" to "x")).code)
		// Another administrator can.
		assertEquals(200, api.put("/users/${admin.id}/roles", api.adminToken(), mapOf("roles" to listOf("ADMIN", "OFFICE"))).status)
	}
}
