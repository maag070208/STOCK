package com.axzydev.stock.api.modules.identity.auth

import com.axzydev.stock.api.core.security.AccessInterceptor
import com.axzydev.stock.api.core.security.AllowedBeforePasswordChange
import com.axzydev.stock.api.core.security.Authenticated
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Public
import com.axzydev.stock.api.core.security.RateLimits
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val username: String?, val password: String?)
data class RefreshRequest(val refreshToken: String?)
data class LogoutRequest(val refreshToken: String?)
data class ChangePasswordRequest(val currentPassword: String?, val newPassword: String?)
data class MeResponse(val user: SessionUser)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService, private val limits: RateLimits) {

	@PostMapping("/login")
	@Public
	fun login(@RequestBody body: LoginRequest, request: HttpServletRequest): LoginResult =
		auth.login(body.username, body.password, AccessInterceptor.channelOf(request), limits.ipOf(request))

	@PostMapping("/refresh")
	@Public
	fun refresh(@RequestBody body: RefreshRequest, request: HttpServletRequest): Tokens =
		auth.refresh(body.refreshToken, AccessInterceptor.channelOf(request))

	@GetMapping("/me")
	@Authenticated
	@AllowedBeforePasswordChange
	fun me(user: CurrentUser): MeResponse = MeResponse(auth.me(user))

	@PatchMapping("/me")
	@Authenticated
	fun updateMe(@RequestBody body: Map<String, Any?>, user: CurrentUser): MeResponse = MeResponse(auth.updateMe(user, body))

	@PostMapping("/logout")
	@Authenticated
	@AllowedBeforePasswordChange
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(@RequestBody(required = false) body: LogoutRequest?, user: CurrentUser) = auth.logout(user, body?.refreshToken)

	@PostMapping("/change-password")
	@Authenticated
	@AllowedBeforePasswordChange
	fun changePassword(@RequestBody body: ChangePasswordRequest, user: CurrentUser): Tokens =
		auth.changePassword(user, body.currentPassword, body.newPassword)
}
