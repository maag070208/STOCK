package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

// F1-14 · e2e: no change may leave the system without somebody who can manage access.
// It runs on its own database, so the only administrators are the ones this test creates.
@TestPropertySource(properties = ["stockflow.test-context=anti-lockout"])
class AntiLockoutTest : ApiTest() {

	@Test
	fun `the last administrator cannot be removed, deactivated or stripped of the permission`() {
		val first = api.adminToken()
		val firstId = api.get("/auth/me", first).json.get("user").get("id").asString()
		val second = api.newUser("ADMIN")

		// With two administrators, one can be demoted.
		assertEquals(200, api.put("/users/${second.id}/roles", first, mapOf("roles" to listOf("OFFICE"))).status)
		assertEquals(200, api.put("/users/${second.id}/roles", first, mapOf("roles" to listOf("ADMIN"))).status)

		// The matrix cannot take roles.manage away from the only role that has it.
		val matrix = api.put("/roles/matrix", first, mapOf("changes" to listOf(mapOf("roleKey" to "ADMIN", "permissionKey" to "roles.manage", "scope" to "NONE"))))
		assertEquals(409, matrix.status)
		assertEquals("ANTI_LOCKOUT", matrix.code)
		assertEquals(200, api.get("/roles/matrix", first).status)

		// The second administrator deactivates the first: one is left, and it is allowed.
		assertEquals(200, api.delete("/users/$firstId", second.token).status)

		// Now the second one is the last. They cannot deactivate themselves, and an exception cannot take the permission away.
		val self = api.delete("/users/${second.id}", second.token)
		assertEquals(409, self.status)
		assertEquals("ANTI_LOCKOUT", self.code)
		assertEquals(200, api.get("/auth/me", second.token).status)

		// The change was rolled back: the first administrator is put back, and takes the roles of the second away.
		assertEquals(200, api.post("/users/$firstId/reactivate", second.token).status)
		val firstAgain = api.login("admin", com.axzydev.stock.api.support.Api.ADMIN_PASSWORD).text("token")
		assertEquals(200, api.put("/users/${second.id}/roles", firstAgain, mapOf("roles" to emptyList<String>())).status)
		val lastOne = api.put("/users/$firstId/permissions/roles.manage", firstAgain, mapOf("scope" to "NONE", "reason" to "x"))
		assertEquals("SELF_ROLE_CHANGE", lastOne.code)
	}
}
