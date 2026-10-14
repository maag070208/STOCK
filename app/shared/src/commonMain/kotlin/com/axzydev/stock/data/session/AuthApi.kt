package com.axzydev.stock.data.session

import com.axzydev.stock.data.api.apiClient
import com.axzydev.stock.data.api.apiError
import com.axzydev.stock.data.api.bodyOrThrow
import com.axzydev.stock.data.api.fromApp
import com.axzydev.stock.data.api.jsonBody
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// The person as the API describes them. `permissions` maps each permission to its scope.
@Serializable
data class SessionUser(
	val id: String,
	val username: String,
	val name: String,
	val email: String? = null,
	val language: String = "es",
	val mustChangePassword: Boolean,
	val roles: List<String>,
	val permissions: Map<String, String> = emptyMap(),
)

@Serializable
data class LoginResult(val token: String, val refreshToken: String, val user: SessionUser)

@Serializable
data class Tokens(val token: String, val refreshToken: String)

@Serializable
private data class Credentials(val username: String, val password: String)

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class PasswordChange(val currentPassword: String, val newPassword: String)

@Serializable
private data class MeResponse(val user: SessionUser)

// The session endpoints of the API (docs/modules/identity.md section 3).
class AuthApi(engine: HttpClientEngine, private val baseUrl: String, private val language: () -> Language) {

	private val http = apiClient(engine)

	suspend fun login(username: String, password: String): LoginResult =
		http.post("$baseUrl/auth/login") { fromApp(language()); jsonBody(); setBody(Credentials(username, password)) }.bodyOrThrow()

	suspend fun refresh(refreshToken: String): Tokens =
		http.post("$baseUrl/auth/refresh") { fromApp(language()); jsonBody(); setBody(RefreshBody(refreshToken)) }.bodyOrThrow()

	suspend fun me(token: String): SessionUser =
		http.get("$baseUrl/auth/me") { fromApp(language(), token) }.bodyOrThrow<MeResponse>().user

	suspend fun changePassword(token: String, currentPassword: String, newPassword: String): Tokens =
		http.post("$baseUrl/auth/change-password") { fromApp(language(), token); jsonBody(); setBody(PasswordChange(currentPassword, newPassword)) }.bodyOrThrow()

	suspend fun logout(token: String, refreshToken: String) {
		val response = http.post("$baseUrl/auth/logout") { fromApp(language(), token); jsonBody(); setBody(RefreshBody(refreshToken)) }
		if (!response.status.isSuccess()) throw response.apiError()
	}
}
