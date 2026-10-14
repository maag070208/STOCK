package com.axzydev.stock.api.modules.identity.users

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RateLimits
import com.axzydev.stock.api.core.security.RequiresPermission
import com.axzydev.stock.api.core.security.policy.PolicyAction
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val users: UserService, private val limits: RateLimits) {

	@PostMapping("/query")
	@RequiresPermission("users.view")
	fun query(@RequestBody request: TableRequest): TablePage<UserView> = users.query(request)

	@GetMapping("/{id}")
	@RequiresPermission("users.view")
	fun get(@PathVariable id: UUID): UserView = users.get(id)

	@PostMapping
	@RequiresPermission("users.create")
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@RequestBody request: CreateUserRequest, user: CurrentUser): CreatedUser = users.create(user, request)

	@PatchMapping("/{id}")
	@RequiresPermission("users.edit")
	fun update(@PathVariable id: UUID, @RequestBody body: Map<String, Any?>, user: CurrentUser): UserView = users.update(user, id, body)

	@DeleteMapping("/{id}")
	@RequiresPermission("users.delete")
	fun deactivate(@PathVariable id: UUID, user: CurrentUser): UserView = users.deactivate(user, id)

	@PostMapping("/{id}/reactivate")
	@RequiresPermission("users.delete")
	fun reactivate(@PathVariable id: UUID, user: CurrentUser): UserView = users.reactivate(user, id)

	// Every request counts toward the rate limit, whoever asks.
	@PostMapping("/{id}/reset-password")
	@RequiresPermission("users.edit")
	fun resetPassword(@PathVariable id: UUID, user: CurrentUser, request: HttpServletRequest): TemporaryPassword {
		val ip = limits.ipOf(request)
		if (limits.reset.blocked(ip)) throw ApiException(ErrorCode.RATE_LIMITED)
		limits.reset.hit(ip)
		return users.resetPassword(user, id)
	}

	@PostMapping("/{id}/unlock")
	@RequiresPermission("users.edit")
	fun unlock(@PathVariable id: UUID, user: CurrentUser): UserView = users.unlock(user, id)

	@PutMapping("/{id}/roles")
	@RequiresPermission("users.permissions")
	fun setRoles(@PathVariable id: UUID, @RequestBody request: SetRolesRequest, user: CurrentUser): UserView = users.setRoles(user, id, request)

	@PutMapping("/{id}/warehouses")
	@RequiresPermission("users.permissions")
	fun setWarehouses(@PathVariable id: UUID, @RequestBody request: SetWarehousesRequest, user: CurrentUser): UserView =
		users.setWarehouses(user, id, request)

	@GetMapping("/{id}/permissions")
	@RequiresPermission("users.permissions")
	fun exceptions(@PathVariable id: UUID): List<PermissionExceptionView> = users.exceptions(id)

	@PutMapping("/{id}/permissions/{permission}")
	@RequiresPermission("users.permissions")
	fun setException(
		@PathVariable id: UUID,
		@PathVariable permission: String,
		@RequestBody request: SetExceptionRequest,
		user: CurrentUser,
	): PermissionExceptionView = users.setException(user, id, permission, request)

	@DeleteMapping("/{id}/permissions/{permission}")
	@RequiresPermission("users.permissions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun removeException(@PathVariable id: UUID, @PathVariable permission: String, user: CurrentUser) = users.removeException(user, id, permission)
}

// The policy action of this module: granting roles or exceptions to a person.
@Configuration
class IdentityPolicyActions {

	@Bean
	fun usersPermissionsSet() = PolicyAction(UserService.ACCESS_ACTION, setOf("target.id", "target.roles", "roles", "permission", "scope"))
}
