package com.axzydev.stock.api.core.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

// The rate limits of the API, per IP. Sign-in counts failed attempts only. A password reset counts every request.
@Component
class RateLimits(
	@Value("\${stockflow.rate-limit.window-minutes}") windowMinutes: Long,
	@Value("\${stockflow.rate-limit.login-max}") loginMax: Int,
	@Value("\${stockflow.rate-limit.reset-max}") resetMax: Int,
	@Value("\${stockflow.trusted-proxy-hops}") private val trustedProxyHops: Int,
	clock: Clock,
) {
	val login = RateLimiter(loginMax, Duration.ofMinutes(windowMinutes), clock)
	val reset = RateLimiter(resetMax, Duration.ofMinutes(windowMinutes), clock)

	fun ipOf(request: HttpServletRequest): String =
		ClientIp.resolve(request.remoteAddr, request.getHeader("X-Forwarded-For"), trustedProxyHops)
}
