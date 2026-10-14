package com.axzydev.stock.api.core.health

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
import java.net.http.HttpResponse

// F0-03 · e2e: health and readiness endpoints over HTTP.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class HealthEndpointTest {

	@LocalServerPort
	var port: Int = 0

	private val client: HttpClient = HttpClient.newHttpClient()

	private fun get(path: String): HttpResponse<String> =
		client.send(
			java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
			HttpResponse.BodyHandlers.ofString(),
		)

	@Test
	fun `health answers 200 with the database status`() {
		val response = get("/api/v1/health")
		assertEquals(200, response.statusCode())
		assertTrue(response.body().contains("\"database\":\"UP\""), response.body())
	}

	@Test
	fun `readiness answers 200 once migrations are applied`() {
		val response = get("/api/v1/health/ready")
		assertEquals(200, response.statusCode())
		assertTrue(response.body().contains("\"status\":\"READY\""), response.body())
	}
}
