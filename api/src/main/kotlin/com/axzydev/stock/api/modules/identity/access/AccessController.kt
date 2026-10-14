package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import com.axzydev.stock.api.core.security.policy.PolicyAction
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

// The access console: the permission catalog, roles, the matrix and policies.
@RestController
@RequestMapping("/api/v1")
class AccessController(private val access: AccessAdminService, private val policies: PolicyAdminService) {

	// Also open to who grants exceptions to people: they need the list of permissions.
	@GetMapping("/permissions")
	@RequiresPermission("roles.manage", "users.permissions")
	fun permissions(): List<PermissionView> = access.permissions()

	@GetMapping("/roles")
	@RequiresPermission("roles.manage", "users.permissions")
	fun roles(): List<RoleView> = access.roles()

	@PostMapping("/roles")
	@RequiresPermission("roles.manage")
	@ResponseStatus(HttpStatus.CREATED)
	fun createRole(@RequestBody request: CreateRoleRequest, user: CurrentUser): RoleView = access.createRole(user, request)

	@GetMapping("/roles/matrix")
	@RequiresPermission("roles.manage")
	fun matrix(): List<MatrixCell> = access.matrix()

	@PutMapping("/roles/matrix")
	@RequiresPermission("roles.manage")
	fun changeMatrix(@RequestBody request: MatrixRequest, user: CurrentUser): List<MatrixCell> = access.changeMatrix(user, request)

	@PatchMapping("/roles/{key}")
	@RequiresPermission("roles.manage")
	fun updateRole(@PathVariable key: String, @RequestBody body: Map<String, Any?>, user: CurrentUser): RoleView = access.updateRole(user, key, body)

	@DeleteMapping("/roles/{key}")
	@RequiresPermission("roles.manage")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deleteRole(@PathVariable key: String, user: CurrentUser) = access.deleteRole(user, key)

	@GetMapping("/policies")
	@RequiresPermission("roles.manage")
	fun policies(): List<PolicyView> = policies.list()

	@GetMapping("/policies/actions")
	@RequiresPermission("roles.manage")
	fun policyActions(): List<PolicyAction> = policies.actions()

	@PostMapping("/policies")
	@RequiresPermission("roles.manage")
	@ResponseStatus(HttpStatus.CREATED)
	fun createPolicy(@RequestBody body: PolicyBody, user: CurrentUser): PolicyView = policies.create(user, body)

	@PutMapping("/policies/{key}")
	@RequiresPermission("roles.manage")
	fun updatePolicy(@PathVariable key: String, @RequestBody body: PolicyBody, user: CurrentUser): PolicyView = policies.update(user, key, body)

	@DeleteMapping("/policies/{key}")
	@RequiresPermission("roles.manage")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deletePolicy(@PathVariable key: String, user: CurrentUser) = policies.delete(user, key)
}
