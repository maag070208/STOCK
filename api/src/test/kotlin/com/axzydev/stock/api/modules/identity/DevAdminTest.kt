package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.modules.identity.users.DevAdmin
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

// The developer's own administrator exists only with the profiles `e2e`/`dev`.
class DevAdminTest {

	@Nested
	inner class WithoutTheProfile : ApiTest() {
		@Autowired
		lateinit var context: ApplicationContext

		@Test
		fun `the developer administrator does not exist`() {
			assertTrue(context.getBeansOfType(DevAdmin::class.java).isEmpty())
			assertEquals("INVALID_CREDENTIALS", api.login("aamaro", "Dev-Admin-Password-1").code)
		}
	}

	@Nested
	@ActiveProfiles("e2e")
	@TestPropertySource(properties = ["stockflow.e2e-admin-password=E2e-Admin-Password-1"])
	inner class WithTheProfile : ApiTest() {
		@Test
		fun `the developer administrator signs in with its password, with no pending change`() {
			val login = api.login("aamaro", "Dev-Admin-Password-1")
			assertEquals(200, login.status)
			assertFalse(login.json.get("user").get("mustChangePassword").asBoolean())
			assertEquals(200, api.post("/users/query", login.text("token"), mapOf<String, Any>()).status)
		}
	}
}
