package com.axzydev.stock.api.core.security

// Where a request comes from. Each channel has its own permission (roles.md section 2).
enum class Channel(val value: String, val permission: String) {
	WEB("web", "web.access"),
	APP("app", "app.access");

	companion object {
		const val HEADER = "X-Client-Channel"

		fun of(value: String?): Channel? = entries.firstOrNull { it.value == value }
	}
}
