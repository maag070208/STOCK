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

// For a developer's own machine. With the profiles `e2e` or `dev`, the API keeps the owner's administrator
// (`stockflow.dev-admin-username`, default `aamaro`) with the password in `stockflow.dev-admin-password`
// (default `123456`), so the owner can always sign in locally. It only writes the password when the account
// is first created: a password the owner changed later is never reset. The profiles are never active in
// production: without them this class does not exist.
@Component
@Profile("e2e", "dev")
class DevAdmin(
	private val users: UserRepository,
	private val passwords: Passwords,
	private val clock: Clock,
	@Value("\${stockflow.dev-admin-username}") private val username: String,
	@Value("\${stockflow.dev-admin-password}") private val password: String,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	override fun run(args: ApplicationArguments) {
		val now = clock.instant()
		val existing = users.findByUsername(username)
		if (existing == null) {
			Passwords.check(password)
			val admin = User(username, "Amaro", email = null, language = "es", passwordHash = passwords.hash(password), createdBy = null, createdAt = now, updatedAt = now)
			admin.mustChangePassword = false
			admin.active = true
			admin.roles.add(SeedRoles.ADMIN)
			users.save(admin)
			log.warn("the profile 'e2e'/'dev' is active: the developer administrator '{}' was created. Never use these profiles in production", username)
			return
		}
		// The account exists: keep it usable, but never touch its password.
		existing.active = true
		existing.lockedAt = null
		existing.failedLogins = 0
		existing.roles.add(SeedRoles.ADMIN)
		existing.updatedAt = now
		users.save(existing)
		log.info("the developer administrator '{}' is ready", username)
	}
}
