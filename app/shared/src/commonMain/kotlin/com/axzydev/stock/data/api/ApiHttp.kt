package com.axzydev.stock.data.api

import com.axzydev.stock.i18n.Language
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiErrorDetail(val field: String, val code: String, val message: String)

// A refusal from the API: its code, and the message in the language of the request.
class ApiError(val status: Int, val code: String, override val message: String, val details: List<ApiErrorDetail> = emptyList()) : Exception(message)

@Serializable
private data class ErrorEnvelope(val code: String = "", val message: String = "", val details: List<ApiErrorDetail> = emptyList())

const val CHANNEL_HEADER = "X-Client-Channel"

// The client every call of the API goes through: JSON both ways, and answers read by the caller, so a refusal
// becomes an ApiError with the API's own message.
fun apiClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
	install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
	expectSuccess = false
}

// Every request says it comes from the app, asks for messages in the person's language, and carries the session when there is one.
fun HttpRequestBuilder.fromApp(language: Language, token: String? = null) {
	header(CHANNEL_HEADER, "app")
	header(HttpHeaders.AcceptLanguage, language.tag)
	if (token != null) bearerAuth(token)
}

fun HttpRequestBuilder.jsonBody() = contentType(ContentType.Application.Json)

suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
	if (!status.isSuccess()) throw apiError()
	return body()
}

suspend fun HttpResponse.apiError(): ApiError {
	val envelope = runCatching { body<ErrorEnvelope>() }.getOrNull()
	return ApiError(status.value, envelope?.code.orEmpty(), envelope?.message ?: status.description, envelope?.details.orEmpty())
}
