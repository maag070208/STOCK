package com.axzydev.stock.api.core.security

// The address a request comes from, for the rate limit.
object ClientIp {

	// X-Forwarded-For is written by whoever sends the request, so it counts only behind proxies we trust.
	// Each trusted proxy added the address it saw: with N of them, the client is the Nth address from the end.
	fun resolve(remoteAddress: String, forwardedFor: String?, trustedHops: Int): String {
		if (trustedHops <= 0 || forwardedFor.isNullOrBlank()) return remoteAddress
		val chain = forwardedFor.split(",").map { it.trim() }.filter { it.isNotEmpty() }
		return chain.getOrNull(chain.size - trustedHops) ?: remoteAddress
	}
}
