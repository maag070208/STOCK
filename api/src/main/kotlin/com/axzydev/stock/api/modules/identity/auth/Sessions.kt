package com.axzydev.stock.api.modules.identity.auth

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.TokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

data class Tokens(val token: String, val refreshToken: String)

// Opens, rotates and ends sessions. A refresh token is 32 random bytes, used once.
@Component
class Sessions(
	private val refreshTokens: RefreshTokenRepository,
	private val tokens: TokenService,
	private val clock: Clock,
	@Value("\${stockflow.jwt.refresh-days}") refreshDays: Long,
) {
	private val lifetime = Duration.ofDays(refreshDays)
	private val random = SecureRandom()

	fun open(userId: UUID): Tokens {
		val refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
		val now = clock.instant()
		refreshTokens.save(RefreshToken(userId, hash(refresh), now.plus(lifetime), now))
		return Tokens(tokens.issue(userId), refresh)
	}

	// The user of a refresh token, which is used up by this call. A used, revoked, expired or unknown token is refused.
	fun consume(refreshToken: String): UUID {
		val now = clock.instant()
		val stored = refreshTokens.findByTokenHash(hash(refreshToken)) ?: throw ApiException(ErrorCode.TOKEN_REVOKED)
		if (!stored.expiresAt.isAfter(now) || refreshTokens.revoke(stored.id!!, now) != 1) throw ApiException(ErrorCode.TOKEN_REVOKED)
		return stored.userId
	}

	// Ends one session of the user. A token of somebody else, or one already ended, changes nothing.
	fun close(userId: UUID, refreshToken: String) {
		val stored = refreshTokens.findByTokenHash(hash(refreshToken)) ?: return
		if (stored.userId == userId) refreshTokens.revoke(stored.id!!, clock.instant())
	}

	fun closeAll(userId: UUID) {
		refreshTokens.revokeAllOf(userId, clock.instant())
	}

	private fun hash(token: String): String =
		MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
