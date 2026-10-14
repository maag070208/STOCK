package com.axzydev.stock.api.core.security

import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

// F1 · e2e: every documented endpoint refuses a request without a token, except the public ones.
// The test reads the OpenAPI document, so a new endpoint is covered without touching it (CYC D-051).
class EndpointAccessTest : ApiTest() {

	private val public = setOf("GET /api/v1/health", "GET /api/v1/health/ready", "POST /api/v1/auth/login", "POST /api/v1/auth/refresh")

	@Test
	fun `every endpoint under the versioned API answers 401 without a token, except the public ones`() {
		val document = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create("http://localhost:$port/docs/json")).GET().build(),
			HttpResponse.BodyHandlers.ofString(),
		)
		val paths = com.axzydev.stock.api.support.Api.mapper.readTree(document.body()).get("paths")
		var checked = 0
		for ((path, operations) in paths.properties()) {
			// Test-only endpoints live under /_probe and are not part of the application.
			if (path.contains("/_probe")) continue
			for (method in operations.propertyNames()) {
				val operation = "${method.uppercase()} $path"
				val url = path.removePrefix("/api/v1").replace(Regex("\\{[^}]+}")) { if (it.value == "{id}") UUID.randomUUID().toString() else "X" }
				val response = api.call(method.uppercase(), url, token = null, body = if (method == "get" || method == "delete") null else mapOf<String, Any>())
				if (operation in public) {
					assertTrue(response.status != 401 || response.code == "INVALID_CREDENTIALS" || response.code == "TOKEN_REVOKED", "$operation must not ask for a token")
				} else {
					assertEquals(401, response.status, operation)
					assertEquals("UNAUTHENTICATED", response.code, operation)
					checked += 1
				}
			}
		}
		assertTrue(checked > 25, "only $checked protected operations were found in the document")
	}
}
