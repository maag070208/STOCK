package com.axzydev.stock.api.modules.identity.auth

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.AccessDenials
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.Channel
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Passwords
import com.axzydev.stock.api.core.security.RateLimits
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.Patch
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.identity.users.IdentityAccountLoader
import com.axzydev.stock.api.modules.identity.users.User
import com.axzydev.stock.api.modules.identity.users.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

// The person as the apps see them. `permissions` holds only the scopes above NONE.
data class SessionUser(
	val id: UUID,
	val username: String,
	val name: String,
	val email: String?,
	val language: String,
	val mustChangePassword: Boolean,
	val roles: List<String>,
	val permissions: Map<String, Scope>,
)

data class LoginResult(val token: String, val refreshToken: String, val user: SessionUser)

@Service
class AuthService(
	private val users: UserRepository,
	private val passwords: Passwords,
	private val sessions: Sessions,
	private val accounts: IdentityAccountLoader,
	private val audit: AuditService,
	private val denials: AccessDenials,
	private val limits: RateLimits,
	private val clock: Clock,
) {
	// A hash of a password nobody knows. A sign-in for a user that does not exist is compared against it.
	private val unknownUserHash = passwords.hash(UUID.randomUUID().toString())

	// A refused sign-in still commits: the failed count and the lock must stay.
	@Transactional(noRollbackFor = [ApiException::class])
	fun login(username: String?, password: String?, channel: Channel, ip: String): LoginResult {
		if (limits.login.blocked(ip)) throw ApiException(ErrorCode.RATE_LIMITED)
		val user = username?.trim()?.lowercase()?.let(users::findByUsername)
		if (user == null || password == null) {
			// The same work and the same answer as a wrong password, so the reply does not tell whether the user exists.
			passwords.matches(password ?: "", unknownUserHash)
			limits.login.hit(ip)
			throw ApiException(ErrorCode.INVALID_CREDENTIALS)
		}
		val actor = Actor(user.id, user.username)
		if (user.locked) {
			limits.login.hit(ip)
			throw ApiException(ErrorCode.ACCOUNT_LOCKED)
		}
		if (!passwords.matches(password, user.passwordHash)) {
			limits.login.hit(ip)
			user.failedLogins += 1
			if (user.failedLogins >= MAX_FAILED_LOGINS) {
				user.lockedAt = clock.instant()
				audit.record(actor, "USER_LOCKED", "user", user.id.toString())
				throw ApiException(ErrorCode.ACCOUNT_LOCKED)
			}
			audit.record(actor, "LOGIN_FAILED", "user", user.id.toString())
			throw ApiException(ErrorCode.INVALID_CREDENTIALS)
		}
		if (!user.active) throw ApiException(ErrorCode.ACCOUNT_DEACTIVATED)
		val current = accounts.currentUser(user)
		requireChannel(current, channel)

		user.failedLogins = 0
		user.lastLoginAt = clock.instant()
		audit.record(actor, "LOGIN", "user", user.id.toString(), after = mapOf("channel" to channel.value))
		val tokens = sessions.open(user.id!!)
		return LoginResult(tokens.token, tokens.refreshToken, sessionUser(user, current))
	}

	@Transactional
	fun refresh(refreshToken: String?, channel: Channel): Tokens {
		val userId = sessions.consume(refreshToken ?: throw ApiException(ErrorCode.TOKEN_REVOKED))
		val user = users.findById(userId).orElseThrow { ApiException(ErrorCode.TOKEN_REVOKED) }
		if (!user.active) throw ApiException(ErrorCode.ACCOUNT_DEACTIVATED)
		requireChannel(accounts.currentUser(user), channel)
		return sessions.open(userId)
	}

	@Transactional(readOnly = true)
	fun me(current: CurrentUser): SessionUser = sessionUser(users.findById(current.id).orElseThrow(), current)

	// A person changes their own preferences. Today that is the language the apps show.
	@Transactional
	fun updateMe(current: CurrentUser, body: Map<String, Any?>): SessionUser {
		val patch = Patch(body, setOf("language"))
		val user = users.findById(current.id).orElseThrow()
		val before = mapOf("language" to user.language)
		val validator = Validator()
		if (patch.has("language")) user.language = validator.required("language", patch.text("language"), Rules::language)
		validator.check()
		user.updatedAt = clock.instant()
		audit.record(current.actor, "USER_UPDATED", "user", current.id.toString(), before, mapOf("language" to user.language))
		return sessionUser(user, current)
	}

	// Ends the session of the given refresh token, or every session of the user when none is given.
	@Transactional
	fun logout(current: CurrentUser, refreshToken: String?) {
		if (refreshToken != null) sessions.close(current.id, refreshToken) else sessions.closeAll(current.id)
		audit.record(current.actor, "LOGOUT", "user", current.id.toString())
	}

	// Every earlier session ends. The answer carries the tokens of a new one.
	@Transactional
	fun changePassword(current: CurrentUser, currentPassword: String?, newPassword: String?): Tokens {
		val user = users.findById(current.id).orElseThrow()
		if (currentPassword == null || !passwords.matches(currentPassword, user.passwordHash)) {
			throw ValidationException(listOf(FieldError("currentPassword", ErrorCode.CURRENT_PASSWORD_WRONG)))
		}
		try {
			Passwords.check(newPassword)
		} catch (e: ApiException) {
			throw ValidationException(listOf(FieldError("newPassword", e.code)))
		}
		if (passwords.matches(newPassword!!, user.passwordHash)) throw ValidationException(listOf(FieldError("newPassword", ErrorCode.PASSWORD_REUSED)))

		user.passwordHash = passwords.hash(newPassword)
		user.mustChangePassword = false
		user.updatedAt = clock.instant()
		sessions.closeAll(user.id!!)
		audit.record(current.actor, "PASSWORD_CHANGED", "user", user.id.toString())
		return sessions.open(user.id!!)
	}

	private fun requireChannel(current: CurrentUser, channel: Channel) {
		if (current.can(channel.permission)) return
		denials.record(current.actor, channel.permission, mapOf("path" to "/api/v1/auth"))
		throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
	}

	private fun sessionUser(user: User, current: CurrentUser) = SessionUser(
		id = user.id!!,
		username = user.username,
		name = user.name,
		email = user.email,
		language = user.language,
		mustChangePassword = user.mustChangePassword,
		roles = user.roles.sorted(),
		permissions = current.permissions,
	)

	companion object {
		const val MAX_FAILED_LOGINS = 5
	}
}
