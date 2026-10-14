package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.Passwords
import com.axzydev.stock.api.modules.identity.access.SeedRoles
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

// While nobody can manage access (a new database, or every administrator locked or deactivated),
// the API gives the user `admin` the password in BOOTSTRAP_ADMIN_PASSWORD, with a pending password change.
// With an administrator who can sign in, it does nothing.
@Component
class BootstrapAdmin(
	private val users: UserRepository,
	private val passwords: Passwords,
	private val audit: AuditService,
	private val clock: Clock,
	@Value("\${stockflow.bootstrap-admin-password}") private val password: String,
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	override fun run(args: ApplicationArguments) {
		if (users.countAccessManagers(clock.instant()) > 0) return
		if (password.isBlank()) {
			log.warn("nobody can manage access and BOOTSTRAP_ADMIN_PASSWORD is not set: nobody can sign in as an administrator")
			return
		}
		Passwords.check(password)
		val now = clock.instant()
		val admin = users.findByUsername(USERNAME)
			?: User(USERNAME, "Administrador", email = null, language = "es", passwordHash = "", createdBy = null, createdAt = now, updatedAt = now)
		admin.passwordHash = passwords.hash(password)
		admin.mustChangePassword = true
		admin.active = true
		admin.lockedAt = null
		admin.failedLogins = 0
		admin.updatedAt = now
		admin.roles.add(SeedRoles.ADMIN)
		val saved = users.save(admin)
		audit.record(Actor.SYSTEM, "USER_BOOTSTRAPPED", "user", saved.id.toString())
		log.info("the administrator account '{}' is ready, with a pending password change", USERNAME)
	}

	companion object {
		const val USERNAME = "admin"
	}
}
