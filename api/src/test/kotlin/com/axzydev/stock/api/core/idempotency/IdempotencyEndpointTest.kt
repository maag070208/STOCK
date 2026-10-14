package com.axzydev.stock.api.core.idempotency

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

// F0-07 · e2e: a repeated Idempotency-Key returns the first response, and reusing it for another request is refused.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class IdempotencyEndpointTest {

	@LocalServerPort
	var port: Int = 0

	private val client = HttpClient.newHttpClient()

	private fun post(key: String, body: String): HttpResponse<String> =
		client.send(
			HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/_probe/idempotent"))
				.header("Content-Type", "application/json")
				.header("Idempotency-Key", key)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build(),
			HttpResponse.BodyHandlers.ofString(),
		)

	@Test
	fun `a repeated key returns the first response, a different request with that key is refused`() {
		val key = "probe-key-${System.nanoTime()}"
		val first = post(key, "{\"name\":\"a\"}")
		val second = post(key, "{\"name\":\"a\"}")
		assertEquals(first.body(), second.body(), "the repeat must return the stored response")

		val third = post(key, "{\"name\":\"different\"}")
		assertEquals(409, third.statusCode())
		assertTrue(third.body().contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\""), third.body())
	}
}
