package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// F1-02 · e2e: users are created by an administrator with a temporary password, and every field is validated.
class UsersEndpointTest : ApiTest() {

	private fun username() = "u" + UUID.randomUUID().toString().replace("-", "").take(10)

	@Test
	fun `creating a user returns a temporary password once, and no read ever shows a password`() {
		val created = api.post("/users", api.adminToken(), mapOf("username" to username(), "name" to "Ana López", "email" to "Ana@Empresa.com.mx", "roles" to listOf("OFFICE")))
		assertEquals(201, created.status)
		val temporary = created.text("temporaryPassword")
		assertEquals(14, temporary.length)
		val user = created.json.get("user")
		assertEquals("ana@empresa.com.mx", user.get("email").asString())
		assertTrue(user.get("mustChangePassword").asBoolean())
		assertEquals(listOf("OFFICE"), user.get("roles").list.map { it.asString() })
		assertEquals(setOf("id", "username", "name", "email", "language", "active", "locked", "mustChangePassword", "roles", "warehouseIds", "lastLoginAt", "createdAt"), user.propertyNames().toSet())

		val read = api.get("/users/${user.get("id").asString()}", api.adminToken())
		assertEquals(200, read.status)
		assertFalse(read.body.contains(temporary), read.body)
		assertFalse(read.body.contains("password", ignoreCase = true) && !read.body.contains("mustChangePassword"), read.body)
		val listed = api.post("/users/query", api.adminToken(), mapOf("filters" to mapOf("username" to user.get("username").asString())))
		assertEquals(1, listed.json.get("total").asInt())
		assertFalse(listed.body.contains(temporary))

		// The temporary password works, and it is different for every user.
		assertEquals(200, api.login(user.get("username").asString(), temporary).status)
		assertNotEquals(temporary, api.createUser("OFFICE").text("temporaryPassword"))
	}

	@Test
	fun `a user without the permission cannot create users, and the refusal is audited`() {
		val office = api.newUser("OFFICE")
		val refused = api.post("/users", office.token, mapOf("username" to username(), "name" to "Otra persona"))
		assertEquals(403, refused.status)
		assertEquals("INSUFFICIENT_PERMISSIONS", refused.code)
		val denied = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "ACCESS_DENIED", "actor" to office.username)))
		assertEquals("users.create", denied.json.get("data").get(0).get("targetId").asString())
	}

	@Test
	fun `every field is validated, and each failing field is named with its code`() {
		val bad = api.post("/users", api.adminToken(), mapOf("username" to "A b", "name" to "X", "email" to "ana@empresa", "language" to "fr", "roles" to listOf("NO_SUCH_ROLE")))
		assertEquals(400, bad.status)
		val details = bad.json.get("details").list.associate { it.get("field").asString() to it.get("code").asString() }
		assertEquals(
			mapOf("username" to "FORMAT_INVALID", "name" to "FORMAT_INVALID", "email" to "EMAIL_INVALID", "language" to "FORMAT_INVALID", "roles" to "CATALOG_VALUE_INVALID"),
			details,
		)
		assertEquals("El correo electrónico no es válido.", bad.json.get("details").list.first { it.get("field").asString() == "email" }.get("message").asString())

		val empty = api.post("/users", api.adminToken(), mapOf("username" to "  ", "name" to ""))
		assertEquals("FIELD_REQUIRED", empty.code)
		assertEquals(listOf("username", "name"), empty.json.get("details").list.map { it.get("field").asString() })
	}

	@Test
	fun `a username is unique, and a field outside the spec is rejected`() {
		val name = username()
		assertEquals(201, api.post("/users", api.adminToken(), mapOf("username" to name, "name" to "Ana López")).status)
		val duplicate = api.post("/users", api.adminToken(), mapOf("username" to name.uppercase(), "name" to "Ana López"))
		assertEquals(409, duplicate.status)
		assertEquals("DUPLICATE_VALUE", duplicate.code)
		assertEquals("username", duplicate.json.get("details").get(0).get("field").asString())

		val unknown = api.post("/users", api.adminToken(), mapOf("username" to username(), "name" to "Ana López", "password" to "i-choose-my-own"))
		assertEquals(400, unknown.status)
		assertEquals("UNKNOWN_FIELD", unknown.code)
		assertEquals("password", unknown.json.get("details").get(0).get("field").asString())
		assertEquals("FORMAT_INVALID", api.call("POST", "/users", api.adminToken(), "{not json").code)
	}

	@Test
	fun `only the fields that are sent change, and an empty email removes it`() {
		val created = api.post("/users", api.adminToken(), mapOf("username" to username(), "name" to "Ana López", "email" to "ana@empresa.com"))
		val id = created.json.get("user").get("id").asString()

		val renamed = api.patch("/users/$id", api.adminToken(), mapOf("name" to "Ana L. Pérez"))
		assertEquals(200, renamed.status)
		assertEquals("Ana L. Pérez", renamed.text("name"))
		assertEquals("ana@empresa.com", renamed.text("email"))

		val cleared = api.patch("/users/$id", api.adminToken(), mapOf("email" to "", "language" to "en"))
		assertTrue(cleared.json.get("email").isNull)
		assertEquals("en", cleared.text("language"))

		assertEquals("UNKNOWN_FIELD", api.patch("/users/$id", api.adminToken(), mapOf("username" to "another")).code)
		assertEquals("UNKNOWN_FIELD", api.patch("/users/$id", api.adminToken(), mapOf("roles" to listOf("ADMIN"))).code)
		assertEquals("EMAIL_INVALID", api.patch("/users/$id", api.adminToken(), mapOf("email" to "ana.empresa.com")).code)
		assertEquals(404, api.patch("/users/${UUID.randomUUID()}", api.adminToken(), mapOf("name" to "Nadie Aquí")).status)
		assertEquals("FORMAT_INVALID", api.get("/users/not-a-uuid", api.adminToken()).code)
	}

	@Test
	fun `a password reset gives a new temporary password, forces the change and ends the sessions`() {
		val user = api.newUser("OFFICE")
		val reset = api.post("/users/${user.id}/reset-password", api.adminToken())
		assertEquals(200, reset.status)
		val temporary = reset.text("temporaryPassword")
		assertEquals("TOKEN_REVOKED", api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken)).code)
		assertEquals("INVALID_CREDENTIALS", api.login(user.username, user.password).code)
		val login = api.login(user.username, temporary)
		assertEquals(200, login.status)
		assertTrue(login.json.get("user").get("mustChangePassword").asBoolean())
	}

	@Test
	fun `the table filters, sorts and pages, and refuses a filter it does not know`() {
		val marker = "zz" + UUID.randomUUID().toString().replace("-", "").take(6)
		listOf("b", "a", "c").forEach { api.post("/users", api.adminToken(), mapOf("username" to "$marker$it", "name" to "Persona $it")) }

		val page = api.post("/users/query", api.adminToken(), mapOf("page" to 1, "limit" to 2, "filters" to mapOf("username" to marker), "sort" to mapOf("key" to "username", "direction" to "desc")))
		assertEquals(200, page.status)
		assertEquals(3, page.json.get("total").asInt())
		assertEquals(listOf("${marker}c", "${marker}b"), page.json.get("data").list.map { it.get("username").asString() })
		assertEquals(2, page.json.get("totalPages").asInt())
		assertTrue(page.json.get("hasNextPage").asBoolean())
		assertFalse(page.json.get("hasPreviousPage").asBoolean())

		val second = api.post("/users/query", api.adminToken(), mapOf("page" to 2, "limit" to 2, "filters" to mapOf("username" to marker), "sort" to mapOf("key" to "username", "direction" to "desc")))
		assertEquals(listOf("${marker}a"), second.json.get("data").list.map { it.get("username").asString() })

		val byRole = api.post("/users/query", api.adminToken(), mapOf("filters" to mapOf("role" to "APP_USER", "active" to true)))
		assertTrue(byRole.json.get("data").list.all { user -> user.get("roles").list.any { it.asString() == "APP_USER" } })

		val unknownFilter = api.post("/users/query", api.adminToken(), mapOf("filters" to mapOf("passwordHash" to "x")))
		assertEquals(400, unknownFilter.status)
		assertEquals("UNKNOWN_FIELD", unknownFilter.code)
		assertEquals("FORMAT_INVALID", api.post("/users/query", api.adminToken(), mapOf("sort" to mapOf("key" to "passwordHash"))).code)
	}
}
