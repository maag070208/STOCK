package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.Clock

// A change is rejected when it leaves nobody who can manage access (roles.md section 5).
// A service calls this at the end of the change, inside its transaction, so the change rolls back when the guard fails.
@Component
class AntiLockout(private val users: UserRepository, private val entities: EntityManager, private val clock: Clock) {

	fun check() {
		// The count is a database query: pending changes are written first so it sees them.
		entities.flush()
		if (users.countAccessManagers(clock.instant()) == 0L) throw ApiException(ErrorCode.ANTI_LOCKOUT)
	}
}
