package com.axzydev.stock.api.core.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// F1-03 · unit: access tokens, passwords, the caller's address and the rate limit.
class TokenServiceTest {

	private val secret = "test-secret-with-at-least-32-bytes-000"
	private val start = Instant.parse("2026-10-13T12:00:00Z")
	private fun at(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

	@Test
	fun `a token gives back its user until it expires`() {
		val user = UUID.randomUUID()
		val token = TokenService(secret, 30, at(start)).issue(user)
		assertEquals(user, TokenService(secret, 30, at(start.plus(Duration.ofMinutes(29)))).verify(token))
		assertNull(TokenService(secret, 30, at(start.plus(Duration.ofMinutes(30)))).verify(token))
	}

	@Test
	fun `a token signed with another secret, or changed, is not valid`() {
		val token = TokenService(secret, 30, at(start)).issue(UUID.randomUUID())
		assertNull(TokenService("another-secret-with-at-least-32-bytes", 30, at(start)).verify(token))
		assertNull(TokenService(secret, 30, at(start)).verify(token.dropLast(2) + "xx"))
		assertNull(TokenService(secret, 30, at(start)).verify("not-a-token"))
	}

	@Test
	fun `a token carries the user id and its times, and nothing about roles or permissions`() {
		val token = TokenService(secret, 30, at(start)).issue(UUID.randomUUID())
		val claims = com.nimbusds.jwt.SignedJWT.parse(token).jwtClaimsSet.claims.keys
		assertEquals(setOf("sub", "iat", "exp"), claims)
	}

	@Test
	fun `a short secret is refused when the service starts`() {
		assertThrows(IllegalArgumentException::class.java) { TokenService("short", 30, at(start)) }
	}

	@Test
	fun `a password is stored as a hash that only the same password matches`() {
		val passwords = Passwords()
		val hash = passwords.hash("correct horse battery")
		assertTrue(hash.startsWith("$2"))
		assertTrue(passwords.matches("correct horse battery", hash))
		assertFalse(passwords.matches("correct horse batterx", hash))
	}

	@Test
	fun `a temporary password is random, long enough and easy to read`() {
		val passwords = Passwords()
		val first = passwords.temporary()
		assertEquals(14, first.length)
		assertNotEquals(first, passwords.temporary())
		assertFalse(first.any { it in "0O1lI" })
		Passwords.check(first)
	}

	@Test
	fun `a password must have 10 to 72 characters`() {
		Passwords.check("a".repeat(10))
		Passwords.check("a".repeat(72))
		assertThrows(com.axzydev.stock.api.core.errors.ApiException::class.java) { Passwords.check("a".repeat(9)) }
		assertThrows(com.axzydev.stock.api.core.errors.ApiException::class.java) { Passwords.check("a".repeat(73)) }
		assertThrows(com.axzydev.stock.api.core.errors.ApiException::class.java) { Passwords.check(null) }
	}

	// F1-06 · unit
	@Test
	fun `without trusted proxies the forwarded header is ignored`() {
		assertEquals("10.0.0.9", ClientIp.resolve("10.0.0.9", "203.0.113.7", trustedHops = 0))
	}

	@Test
	fun `with trusted proxies the client is the address just before them`() {
		assertEquals("203.0.113.7", ClientIp.resolve("10.0.0.9", "203.0.113.7", trustedHops = 1))
		assertEquals("203.0.113.7", ClientIp.resolve("10.0.0.9", "198.51.100.1, 203.0.113.7, 10.0.0.2", trustedHops = 2))
		// A client cannot push its own entry past the proxies we trust.
		assertEquals("203.0.113.7", ClientIp.resolve("10.0.0.9", "1.2.3.4, 203.0.113.7", trustedHops = 1))
		assertEquals("10.0.0.9", ClientIp.resolve("10.0.0.9", null, trustedHops = 1))
	}

	@Test
	fun `the limiter blocks a key at its maximum and frees it when the window ends`() {
		var now = start
		val clock = object : Clock() {
			override fun instant() = now
			override fun getZone() = ZoneOffset.UTC
			override fun withZone(zone: java.time.ZoneId) = this
		}
		val limiter = RateLimiter(max = 3, window = Duration.ofMinutes(15), clock = clock)
		repeat(2) { limiter.hit("203.0.113.7") }
		assertFalse(limiter.blocked("203.0.113.7"))
		limiter.hit("203.0.113.7")
		assertTrue(limiter.blocked("203.0.113.7"))
		assertFalse(limiter.blocked("198.51.100.1"))
		now = start.plus(Duration.ofMinutes(15))
		assertFalse(limiter.blocked("203.0.113.7"))
	}
}
