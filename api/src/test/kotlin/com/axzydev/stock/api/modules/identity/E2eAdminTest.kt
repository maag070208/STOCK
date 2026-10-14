package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.modules.identity.users.E2eAdmin
import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

// The test administrator exists only with the profile `e2e`.
class E2eAdminTest {

	@Nested
	inner class WithoutTheProfile : ApiTest() {
		@Autowired
		lateinit var context: ApplicationContext

		@Test
		fun `the test administrator does not exist`() {
			assertTrue(context.getBeansOfType(E2eAdmin::class.java).isEmpty())
			assertEquals("INVALID_CREDENTIALS", api.login("e2e-admin", "E2e-Admin-Password-1").code)
		}
	}

	@Nested
	@ActiveProfiles("e2e")
	@TestPropertySource(properties = ["stockflow.e2e-admin-password=E2e-Admin-Password-1"])
	inner class WithTheProfile : ApiTest() {
		@Test
		fun `the profile raises the rate limits, so the suites do not trip them`(@Autowired limits: com.axzydev.stock.api.core.security.RateLimits) {
			repeat(30) { limits.login.hit("203.0.113.9") }
			org.junit.jupiter.api.Assertions.assertFalse(limits.login.blocked("203.0.113.9"))
		}

		@Test
		fun `the test administrator signs in with its password, with no pending change`() {
			val login = api.login("e2e-admin", "E2e-Admin-Password-1")
			assertEquals(200, login.status)
			assertFalse(login.json.get("user").get("mustChangePassword").asBoolean())
			assertEquals(200, api.post("/users/query", login.text("token"), mapOf<String, Any>()).status)
		}
	}
}
