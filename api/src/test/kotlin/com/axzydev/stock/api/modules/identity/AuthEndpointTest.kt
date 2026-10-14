package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F1-03, F1-04, F1-05 and F1-07 · e2e: signing in, sessions, the forced password change, the lockout and the channels.
class AuthEndpointTest : ApiTest() {

	@Test
	fun `login returns both tokens and the user with their permissions, and never the password`() {
		val user = api.newUser("OFFICE")
		val login = api.login(user.username, user.password)
		assertEquals(200, login.status)
		assertTrue(login.text("token").isNotBlank())
		assertTrue(login.text("refreshToken").isNotBlank())
		assertEquals(user.username, login.json.get("user").get("username").asString())
		assertEquals(listOf("OFFICE"), login.json.get("user").get("roles").list.map { it.asString() })
		assertEquals("ALL", login.json.get("user").get("permissions").get("web.access").asString())
		assertFalse(login.body.contains("password", ignoreCase = true) && login.body.contains("\$2"), login.body)
		assertFalse(login.body.contains("passwordHash"), login.body)
	}

	@Test
	fun `a wrong password and an unknown user get the same answer`() {
		val user = api.newUser("OFFICE")
		val wrong = api.login(user.username, "not-the-password")
		val unknown = api.login("nobody-" + user.username, "not-the-password")
		assertEquals(401, wrong.status)
		assertEquals("INVALID_CREDENTIALS", wrong.code)
		assertEquals(401, unknown.status)
		assertEquals(wrong.body, unknown.body)
	}

	@Test
	fun `a request without a token, or with a bad one, is unauthenticated`() {
		assertEquals("UNAUTHENTICATED", api.get("/auth/me").code)
		assertEquals(401, api.get("/auth/me").status)
		assertEquals("UNAUTHENTICATED", api.get("/auth/me", token = "not-a-token").code)
		assertEquals("UNAUTHENTICATED", api.post("/users/query", body = mapOf<String, Any>()).code)
	}

	@Test
	fun `refreshing gives new tokens, and the old refresh token stops working`() {
		val user = api.newUser("OFFICE")
		val refreshed = api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken))
		assertEquals(200, refreshed.status)
		assertEquals(200, api.get("/auth/me", refreshed.text("token")).status)

		val again = api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken))
		assertEquals(401, again.status)
		assertEquals("TOKEN_REVOKED", again.code)
		assertEquals("TOKEN_REVOKED", api.post("/auth/refresh", body = mapOf("refreshToken" to "made-up")).code)
		assertEquals(200, api.post("/auth/refresh", body = mapOf("refreshToken" to refreshed.text("refreshToken"))).status)
	}

	@Test
	fun `logging out ends the session of that refresh token`() {
		val user = api.newUser("OFFICE")
		assertEquals(204, api.post("/auth/logout", user.token, mapOf("refreshToken" to user.refreshToken)).status)
		assertEquals("TOKEN_REVOKED", api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken)).code)
	}

	@Test
	fun `after a password change the old refresh token fails and the new tokens work`() {
		val user = api.newUser("OFFICE")
		val changed = api.post("/auth/change-password", user.token, mapOf("currentPassword" to user.password, "newPassword" to "Another-Password-9"))
		assertEquals(200, changed.status)
		assertEquals("TOKEN_REVOKED", api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken)).code)
		assertEquals(200, api.get("/auth/me", changed.text("token")).status)
		assertEquals(200, api.post("/auth/refresh", body = mapOf("refreshToken" to changed.text("refreshToken"))).status)
		assertEquals(200, api.login(user.username, "Another-Password-9").status)
		assertEquals("INVALID_CREDENTIALS", api.login(user.username, user.password).code)
	}

	@Test
	fun `a password change is refused with the wrong current password, a short one, or the same one`() {
		val user = api.newUser("OFFICE")
		val change = { current: String, new: String -> api.post("/auth/change-password", user.token, mapOf("currentPassword" to current, "newPassword" to new)) }
		val wrong = change("not-the-password", "Another-Password-9")
		assertEquals(400, wrong.status)
		assertEquals("CURRENT_PASSWORD_WRONG", wrong.code)
		assertEquals("currentPassword", wrong.json.get("details").get(0).get("field").asString())
		assertEquals("PASSWORD_INVALID", change(user.password, "short").code)
		assertEquals("PASSWORD_REUSED", change(user.password, user.password).code)
	}

	@Test
	fun `a user with a pending password change can only see their session and change the password`() {
		val created = api.createUser("ADMIN")
		val username = created.json.get("user").get("username").asString()
		val temporary = created.text("temporaryPassword")
		val login = api.login(username, temporary)
		assertEquals(200, login.status)
		assertTrue(login.json.get("user").get("mustChangePassword").asBoolean())
		val token = login.text("token")

		val blocked = api.post("/users/query", token, mapOf<String, Any>())
		assertEquals(403, blocked.status)
		assertEquals("PASSWORD_CHANGE_REQUIRED", blocked.code)
		assertEquals("PASSWORD_CHANGE_REQUIRED", api.get("/roles", token).code)
		assertEquals(200, api.get("/auth/me", token).status)

		val changed = api.post("/auth/change-password", token, mapOf("currentPassword" to temporary, "newPassword" to "My-Own-Password-1"))
		assertEquals(200, changed.status)
		assertEquals(200, api.post("/users/query", changed.text("token"), mapOf<String, Any>()).status)
		assertFalse(api.get("/auth/me", changed.text("token")).json.get("user").get("mustChangePassword").asBoolean())
	}

	@Test
	fun `five wrong passwords lock the account until an administrator unlocks it, and the unlock is audited`() {
		val user = api.newUser("OFFICE")
		repeat(4) { assertEquals("INVALID_CREDENTIALS", api.login(user.username, "wrong-password").code) }
		val fifth = api.login(user.username, "wrong-password")
		assertEquals(423, fifth.status)
		assertEquals("ACCOUNT_LOCKED", fifth.code)
		// The correct password is refused while the account is locked.
		assertEquals("ACCOUNT_LOCKED", api.login(user.username, user.password).code)

		val unlocked = api.post("/users/${user.id}/unlock", api.adminToken())
		assertEquals(200, unlocked.status)
		assertFalse(unlocked.json.get("locked").asBoolean())
		assertEquals(200, api.login(user.username, user.password).status)

		val audit = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("targetId" to user.id, "action" to "USER_UNLOCKED")))
		assertEquals(1, audit.json.get("total").asInt())
		assertEquals("admin", audit.json.get("data").get(0).get("actor").asString())
	}

	@Test
	fun `a success before the fifth failure resets the count`() {
		val user = api.newUser("OFFICE")
		repeat(4) { api.login(user.username, "wrong-password") }
		assertEquals(200, api.login(user.username, user.password).status)
		repeat(4) { assertEquals("INVALID_CREDENTIALS", api.login(user.username, "wrong-password").code) }
		assertEquals(200, api.login(user.username, user.password).status)
	}

	@Test
	fun `an app user signs in on the app, and is refused on the web`() {
		val user = api.newUser("APP_USER", channel = "app")
		assertEquals(200, api.get("/auth/me", user.token, channel = "app").status)

		val onWeb = api.get("/auth/me", user.token, channel = "web")
		assertEquals(403, onWeb.status)
		assertEquals("INSUFFICIENT_PERMISSIONS", onWeb.code)
		val webLogin = api.login(user.username, user.password, channel = "web")
		assertEquals(403, webLogin.status)
		assertEquals("INSUFFICIENT_PERMISSIONS", webLogin.code)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken), channel = "web").code)

		val denied = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "ACCESS_DENIED", "actor" to user.username)))
		assertTrue(denied.json.get("total").asInt() >= 2, denied.body)
		assertEquals("web.access", denied.json.get("data").get(0).get("targetId").asString())
	}

	@Test
	fun `an administrator signs in on both channels, and an office user only on the web`() {
		assertEquals(200, api.get("/auth/me", api.adminToken(), channel = "web").status)
		assertEquals(200, api.get("/auth/me", api.adminToken(), channel = "app").status)
		val office = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/auth/me", office.token, channel = "app").code)
	}

	@Test
	fun `a request must say which channel it comes from`() {
		val missing = api.get("/auth/me", api.adminToken(), channel = null)
		assertEquals(400, missing.status)
		assertEquals("FORMAT_INVALID", missing.code)
		assertEquals("X-Client-Channel", missing.json.get("details").get(0).get("field").asString())
		assertEquals("FORMAT_INVALID", api.get("/auth/me", api.adminToken(), channel = "desktop").code)
		assertEquals("FORMAT_INVALID", api.post("/auth/login", body = mapOf("username" to "admin", "password" to "x"), channel = null).code)
	}

	@Test
	fun `a deactivated account cannot sign in, and its sessions stop working`() {
		val user = api.newUser("OFFICE")
		assertEquals(200, api.delete("/users/${user.id}", api.adminToken()).status)
		val login = api.login(user.username, user.password)
		assertEquals(403, login.status)
		assertEquals("ACCOUNT_DEACTIVATED", login.code)
		assertEquals("ACCOUNT_DEACTIVATED", api.get("/auth/me", user.token).code)
		assertEquals("TOKEN_REVOKED", api.post("/auth/refresh", body = mapOf("refreshToken" to user.refreshToken)).code)

		assertEquals(200, api.post("/users/${user.id}/reactivate", api.adminToken()).status)
		assertEquals(200, api.login(user.username, user.password).status)
	}

	@Test
	fun `a person saves their own language, and nothing else of their profile`() {
		val user = api.newUser("OFFICE")
		val changed = api.patch("/auth/me", user.token, mapOf("language" to "en"))
		assertEquals(200, changed.status)
		assertEquals("en", changed.json.get("user").get("language").asString())
		assertEquals("en", api.get("/auth/me", user.token).json.get("user").get("language").asString())
		assertEquals("FORMAT_INVALID", api.patch("/auth/me", user.token, mapOf("language" to "fr")).code)
		assertEquals("UNKNOWN_FIELD", api.patch("/auth/me", user.token, mapOf("name" to "Otro nombre")).code)
		assertEquals("UNKNOWN_FIELD", api.patch("/auth/me", user.token, mapOf("roles" to listOf("ADMIN"))).code)
	}

	@Test
	fun `error messages are in Spanish by default and in English on request`() {
		assertEquals("Inicia sesión para continuar.", api.get("/auth/me").text("message"))
		assertEquals("Sign in to continue.", api.call("GET", "/auth/me", headers = mapOf("Accept-Language" to "en")).text("message"))
	}
}
