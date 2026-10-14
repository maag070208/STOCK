package com.axzydev.stock.api.modules.identity.auth

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

// A session. The token itself is never stored: only its SHA-256 hash.
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
	var userId: UUID,
	var tokenHash: String,
	var expiresAt: Instant,
	var createdAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var revokedAt: Instant? = null
}

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

	fun findByTokenHash(tokenHash: String): RefreshToken?

	// Marks the token as used, only if nobody used it first. Returns 1 when this call won.
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
	fun revoke(id: UUID, now: Instant): Int

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
	fun revokeAllOf(userId: UUID, now: Instant): Int
}
