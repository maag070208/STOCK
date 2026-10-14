package com.axzydev.stock.api.modules.identity

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.AccessDenials
import com.axzydev.stock.api.core.security.Channel
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Passwords
import com.axzydev.stock.api.core.security.RateLimits
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.security.TokenService
import com.axzydev.stock.api.modules.identity.auth.AuthService
import com.axzydev.stock.api.modules.identity.auth.RefreshToken
import com.axzydev.stock.api.modules.identity.auth.RefreshTokenRepository
import com.axzydev.stock.api.modules.identity.auth.Sessions
import com.axzydev.stock.api.modules.identity.auth.Tokens
import com.axzydev.stock.api.modules.identity.users.IdentityAccountLoader
import com.axzydev.stock.api.modules.identity.users.User
import com.axzydev.stock.api.modules.identity.users.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

// F1-03 to F1-06 · unit: sessions, the password change, the lockout and what the rate limit counts.
class AuthServiceTest {

	private val clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC)
	private val passwords = Passwords()
	private val users = mockk<UserRepository>()
	private val sessions = mockk<Sessions>(relaxed = true)
	private val accounts = mockk<IdentityAccountLoader>()
	private val audit = mockk<AuditService>(relaxed = true)
	private val limits = RateLimits(windowMinutes = 15, loginMax = 100, resetMax = 100, trustedProxyHops = 0, clock = clock)
	private val service = AuthService(users, passwords, sessions, accounts, audit, mockk<AccessDenials>(relaxed = true), limits, clock)

	private val ana = User("ana", "Ana López", null, "es", passwords.hash("correct-password"), null, clock.instant(), clock.instant()).apply {
		id = UUID.randomUUID()
		mustChangePassword = false
	}
	private val current = CurrentUser(ana.id!!, "ana", emptySet(), emptySet(), mapOf("web.access" to Scope.ALL), false)

	init {
		every { users.findByUsername("ana") } returns ana
		every { users.findByUsername("nobody") } returns null
		every { users.findById(ana.id!!) } returns Optional.of(ana)
		every { accounts.currentUser(ana) } returns current
		every { sessions.open(any()) } returns Tokens("access", "refresh")
	}

	private fun login(password: String, username: String = "ana") = service.login(username, password, Channel.WEB, "203.0.113.7")

	private fun failedLogin(password: String = "wrong-password", username: String = "ana"): ErrorCode =
		assertThrows(ApiException::class.java) { login(password, username) }.code

	@Test
	fun `the fifth failed login locks the account`() {
		repeat(4) { assertEquals(ErrorCode.INVALID_CREDENTIALS, failedLogin()) }
		assertFalse(ana.locked)
		assertEquals(ErrorCode.ACCOUNT_LOCKED, failedLogin())
		assertTrue(ana.locked)
		// Locked: the correct password is refused too.
		assertEquals(ErrorCode.ACCOUNT_LOCKED, failedLogin("correct-password"))
		verify(exactly = 1) { audit.record(any(), "USER_LOCKED", "user", ana.id.toString(), any(), any()) }
	}

	@Test
	fun `a success before the fifth failure resets the count`() {
		repeat(4) { failedLogin() }
		login("correct-password")
		assertEquals(0, ana.failedLogins)
		repeat(4) { assertEquals(ErrorCode.INVALID_CREDENTIALS, failedLogin()) }
		assertFalse(ana.locked)
	}

	@Test
	fun `an unknown user gets the same answer as a wrong password`() {
		assertEquals(ErrorCode.INVALID_CREDENTIALS, failedLogin(username = "nobody"))
	}

	@Test
	fun `a user without the channel's permission cannot sign in on it`() {
		val error = assertThrows(ApiException::class.java) { service.login("ana", "correct-password", Channel.APP, "203.0.113.7") }
		assertEquals(ErrorCode.INSUFFICIENT_PERMISSIONS, error.code)
	}

	@Test
	fun `the rate limit counts failed logins only`() {
		val strict = RateLimits(windowMinutes = 15, loginMax = 2, resetMax = 100, trustedProxyHops = 0, clock = clock)
		val limited = AuthService(users, passwords, sessions, accounts, audit, mockk(relaxed = true), strict, clock)
		repeat(5) { limited.login("ana", "correct-password", Channel.WEB, "203.0.113.7") }
		assertFalse(strict.login.blocked("203.0.113.7"))
		repeat(2) { assertThrows(ApiException::class.java) { limited.login("nobody", "wrong-password", Channel.WEB, "203.0.113.7") } }
		assertTrue(strict.login.blocked("203.0.113.7"))
		val error = assertThrows(ApiException::class.java) { limited.login("ana", "correct-password", Channel.WEB, "203.0.113.7") }
		assertEquals(ErrorCode.RATE_LIMITED, error.code)
		// Another address is not affected.
		limited.login("ana", "correct-password", Channel.WEB, "198.51.100.1")
	}

	@Test
	fun `changing the password revokes every refresh token of the user and opens a new session`() {
		val tokens = service.changePassword(current, "correct-password", "a-brand-new-password")
		verify(exactly = 1) { sessions.closeAll(ana.id!!) }
		assertEquals(Tokens("access", "refresh"), tokens)
		assertTrue(passwords.matches("a-brand-new-password", ana.passwordHash))
		assertFalse(ana.mustChangePassword)
	}

	@Test
	fun `a password change needs the current password, a valid new one, and a different one`() {
		val code = { currentPassword: String, newPassword: String ->
			assertThrows(com.axzydev.stock.api.core.validation.ValidationException::class.java) {
				service.changePassword(current, currentPassword, newPassword)
			}.errors.single().code
		}
		assertEquals(ErrorCode.CURRENT_PASSWORD_WRONG, code("wrong-password", "a-brand-new-password"))
		assertEquals(ErrorCode.PASSWORD_INVALID, code("correct-password", "abcd"))
		assertEquals(ErrorCode.PASSWORD_REUSED, code("correct-password", "correct-password"))
		verify(exactly = 0) { sessions.closeAll(any()) }
	}

	// The real Sessions with a repository that behaves like the table: a token can be revoked once.
	@Test
	fun `rotating a refresh token invalidates the previous one`() {
		val stored = mutableMapOf<String, RefreshToken>()
		val repository = mockk<RefreshTokenRepository>()
		val saved = slot<RefreshToken>()
		every { repository.save(capture(saved)) } answers { saved.captured.apply { id = UUID.randomUUID() }.also { stored[it.tokenHash] = it } }
		every { repository.findByTokenHash(any()) } answers { stored[firstArg()] }
		every { repository.revoke(any(), any()) } answers {
			val token = stored.values.first { it.id == firstArg<UUID>() }
			if (token.revokedAt == null) 1.also { token.revokedAt = secondArg() } else 0
		}
		val real = Sessions(repository, TokenService("test-secret-with-at-least-32-bytes-000", 30, clock), clock, refreshDays = 30)

		val first = real.open(ana.id!!)
		assertNull(stored.values.single().revokedAt)
		assertNotEquals(first.refreshToken, stored.values.single().tokenHash, "the token is stored as a hash")
		assertEquals(ana.id, real.consume(first.refreshToken))
		assertNotNull(stored.values.single().revokedAt)
		assertEquals(ErrorCode.TOKEN_REVOKED, assertThrows(ApiException::class.java) { real.consume(first.refreshToken) }.code)
		assertEquals(ErrorCode.TOKEN_REVOKED, assertThrows(ApiException::class.java) { real.consume("unknown-token") }.code)
	}
}
