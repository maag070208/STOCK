package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.HandlerMethod
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// The checks of every request under /api/v1, in the order of docs/modules/identity.md section 1.
// Access is resolved here, on the server, from the database: the token only says who the person is.
@Component
class AccessInterceptor(
	private val tokens: TokenService,
	private val accounts: AccountLoader,
	private val denials: AccessDenials,
) : HandlerInterceptor {

	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		if (handler !is HandlerMethod) return true
		if (handler.hasMethodAnnotation(Public::class.java)) return true
		val required = handler.getMethodAnnotation(RequiresPermission::class.java)
		// An endpoint that declares nothing is closed. The API also refuses to start with one (AccessDeclarationCheck).
		if (required == null && !handler.hasMethodAnnotation(Authenticated::class.java)) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)

		val token = request.getHeader("Authorization")?.takeIf { it.startsWith(BEARER) }?.substring(BEARER.length)
		val userId = token?.let(tokens::verify) ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
		val account = accounts.load(userId) ?: throw ApiException(ErrorCode.UNAUTHENTICATED)
		if (!account.active) throw ApiException(ErrorCode.ACCOUNT_DEACTIVATED)
		val user = account.user

		val channel = channelOf(request)
		if (!user.can(channel.permission)) deny(user, channel.permission, request)
		if (user.mustChangePassword && !handler.hasMethodAnnotation(AllowedBeforePasswordChange::class.java)) {
			throw ApiException(ErrorCode.PASSWORD_CHANGE_REQUIRED)
		}
		if (required != null && required.anyOf.none(user::can)) deny(user, required.anyOf.joinToString(" | "), request)

		request.setAttribute(CURRENT_USER, user)
		return true
	}

	private fun deny(user: CurrentUser, permission: String, request: HttpServletRequest): Nothing {
		denials.record(user.actor, permission, mapOf("method" to request.method, "path" to request.requestURI))
		throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
	}

	companion object {
		const val CURRENT_USER = "stockflow.currentUser"
		private const val BEARER = "Bearer "

		// Every request says where it comes from: the web or the app.
		fun channelOf(request: HttpServletRequest): Channel =
			Channel.of(request.getHeader(Channel.HEADER)) ?: throw ValidationException(listOf(FieldError(Channel.HEADER, ErrorCode.FORMAT_INVALID)))
	}
}

// Lets a controller take the signed-in person as a parameter: fun me(user: CurrentUser).
@Component
class CurrentUserResolver : HandlerMethodArgumentResolver {

	override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == CurrentUser::class.java

	override fun resolveArgument(
		parameter: MethodParameter,
		mavContainer: ModelAndViewContainer?,
		webRequest: NativeWebRequest,
		binderFactory: WebDataBinderFactory?,
	): Any = webRequest.getAttribute(AccessInterceptor.CURRENT_USER, RequestAttributes.SCOPE_REQUEST)
		?: throw ApiException(ErrorCode.UNAUTHENTICATED)
}

@org.springframework.context.annotation.Configuration
class SecurityWebConfig(
	private val interceptor: AccessInterceptor,
	private val currentUser: CurrentUserResolver,
) : WebMvcConfigurer {

	override fun addInterceptors(registry: InterceptorRegistry) {
		registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**")
	}

	override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
		resolvers += currentUser
	}
}
