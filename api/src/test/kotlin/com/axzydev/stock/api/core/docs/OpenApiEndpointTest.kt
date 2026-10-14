package com.axzydev.stock.api.core.docs

import com.axzydev.stock.api.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// F0-06 · e2e: the OpenAPI JSON is served at /docs/json and lists the health endpoints.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OpenApiEndpointTest {

	@LocalServerPort
	var port: Int = 0

	@Test
	fun `docs json lists both health endpoints`() {
		val response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create("http://localhost:$port/docs/json")).GET().build(),
			HttpResponse.BodyHandlers.ofString(),
		)
		assertEquals(200, response.statusCode())
		assertTrue(response.body().contains("\"/api/v1/health\""), "health missing from the document")
		assertTrue(response.body().contains("\"/api/v1/health/ready\""), "ready missing from the document")
	}
}
