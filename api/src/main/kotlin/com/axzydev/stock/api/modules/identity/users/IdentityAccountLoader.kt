package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.security.AccountLoader
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.LoadedAccount
import com.axzydev.stock.api.core.security.PermissionException
import com.axzydev.stock.api.modules.identity.access.AccessCache
import com.axzydev.stock.api.modules.identity.access.UserPermissionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

// Resolves a person's access for one request: their roles, exceptions and warehouses come from the database now,
// so a change by an administrator applies on the next request.
@Component
class IdentityAccountLoader(
	private val users: UserRepository,
	private val exceptions: UserPermissionRepository,
	private val access: AccessCache,
	private val clock: Clock,
) : AccountLoader {

	@Transactional(readOnly = true)
	override fun load(userId: UUID): LoadedAccount? {
		val user = users.findById(userId).orElse(null) ?: return null
		return LoadedAccount(currentUser(user), user.active)
	}

	fun currentUser(user: User): CurrentUser {
		val granted = exceptions.findByUserId(user.id!!).map { PermissionException(it.permissionKey, it.scope, it.expiresAt) }
		return CurrentUser(
			id = user.id!!,
			username = user.username,
			roles = user.roles.toSet(),
			warehouseIds = user.warehouseIds.toSet(),
			permissions = access.snapshot.scopesOf(user.roles, granted, clock.instant()),
			mustChangePassword = user.mustChangePassword,
		)
	}
}
