package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

// F1-06 · e2e: the rate limit per IP, with low limits so the test can reach them.
@TestPropertySource(properties = ["stockflow.rate-limit.login-max=3", "stockflow.rate-limit.reset-max=2"])
class RateLimitTest : ApiTest() {

	@Test
	fun `failed logins and password resets from one address are limited, with the standard envelope`() {
		// Set up first: successful sign-ins do not count.
		val admin = api.adminToken()
		val user = api.newUser("OFFICE")
		repeat(5) { assertEquals(200, api.login(user.username, user.password).status) }

		// A password reset counts every request.
		repeat(2) { assertEquals(200, api.post("/users/${user.id}/reset-password", admin).status) }
		val reset = api.post("/users/${user.id}/reset-password", admin)
		assertEquals(429, reset.status)
		assertEquals("RATE_LIMITED", reset.code)

		// Three failed sign-ins use up the limit. Different names, so no account is locked.
		repeat(3) { assertEquals("INVALID_CREDENTIALS", api.login("nobody-$it", "wrong-password").code) }
		val limited = api.login("admin", com.axzydev.stock.api.support.Api.ADMIN_PASSWORD)
		assertEquals(429, limited.status)
		assertEquals("RATE_LIMITED", limited.code)
		assertEquals("Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.", limited.text("message"))
		// A session that is already open keeps working.
		assertEquals(200, api.get("/auth/me", admin).status)
	}
}
