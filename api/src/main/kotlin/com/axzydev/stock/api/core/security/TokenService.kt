package com.axzydev.stock.api.core.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID

// Access tokens: a JWT signed with HS256 that carries the user id and nothing else.
// Roles and permissions are never in the token. They are read from the database on every request.
@Service
class TokenService(
	@Value("\${stockflow.jwt.secret}") secret: String,
	@Value("\${stockflow.jwt.access-minutes}") accessMinutes: Long,
	private val clock: Clock,
) {
	private val key = secret.toByteArray()
	private val lifetime = Duration.ofMinutes(accessMinutes)

	init {
		require(key.size >= 32) { "JWT_SECRET must be at least 32 bytes" }
	}

	fun issue(userId: UUID): String {
		val now = clock.instant()
		val claims = JWTClaimsSet.Builder()
			.subject(userId.toString())
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plus(lifetime)))
			.build()
		return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).apply { sign(MACSigner(key)) }.serialize()
	}

	// The user id of a valid, unexpired token. Null for anything else.
	fun verify(token: String): UUID? =
		try {
			val jwt = SignedJWT.parse(token)
			val expires = jwt.jwtClaimsSet.expirationTime?.toInstant()
			if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(MACVerifier(key))) null
			else if (expires == null || !expires.isAfter(clock.instant())) null
			else UUID.fromString(jwt.jwtClaimsSet.subject)
		} catch (e: Exception) {
			null
		}
}
