package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.security.Passwords
import com.axzydev.stock.api.modules.identity.access.SeedRoles
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

// For end-to-end suites only. With the profile `e2e`, the API keeps an administrator `e2e-admin` whose password is
// E2E_ADMIN_PASSWORD, so the web and app suites can set up their data without depending on a person's account.
// The profile is never active in production: without it this class does not exist.
@Component
@Profile("e2e")
class E2eAdmin(
	private val users: UserRepository,
	private val passwords: Passwords,
	private val clock: Clock,
	@Value("\${stockflow.e2e-admin-password}") private val password: String,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	override fun run(args: ApplicationArguments) {
		Passwords.check(password)
		val now = clock.instant()
		val admin = users.findByUsername(USERNAME)
			?: User(USERNAME, "E2E administrator", email = null, language = "es", passwordHash = "", createdBy = null, createdAt = now, updatedAt = now)
		admin.passwordHash = passwords.hash(password)
		admin.mustChangePassword = false
		admin.active = true
		admin.lockedAt = null
		admin.failedLogins = 0
		admin.updatedAt = now
		admin.roles.add(SeedRoles.ADMIN)
		users.save(admin)
		log.warn("the profile 'e2e' is active: the test administrator '{}' exists. Never use this profile in production", USERNAME)
	}

	companion object {
		const val USERNAME = "e2e-admin"
	}
}
