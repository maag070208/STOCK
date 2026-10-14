package com.axzydev.stock.api.core.errors

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

// F0-04 and F0-05 · e2e: the envelope over HTTP, in Spanish by default and in English on request.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ErrorEnvelopeEndpointTest {

	@LocalServerPort
	var port: Int = 0

	private val client = HttpClient.newHttpClient()

	private fun postJson(body: String, language: String? = null): HttpResponse<String> {
		val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/_probe/echo"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
		if (language != null) builder.header("Accept-Language", language)
		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
	}

	@Test
	fun `a malformed body returns the flat envelope with FORMAT_INVALID in Spanish by default`() {
		val response = postJson("{not json")
		assertEquals(400, response.statusCode())
		assertTrue(response.body().contains("\"code\":\"FORMAT_INVALID\""), response.body())
		assertTrue(response.body().contains("\"error\":"), response.body())
		assertTrue(response.body().contains("\"message\":\"El formato no es válido.\""), response.body())
	}

	@Test
	fun `the same error in English when the request asks for English`() {
		val response = postJson("{not json", language = "en")
		assertTrue(response.body().contains("\"message\":\"The format is not valid.\""), response.body())
	}

	@Test
	fun `an unexpected failure returns INTERNAL_ERROR without a stack trace`() {
		val response = client.send(
			HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/_probe/boom")).GET().build(),
			HttpResponse.BodyHandlers.ofString(),
		)
		assertEquals(500, response.statusCode())
		assertTrue(response.body().contains("\"code\":\"INTERNAL_ERROR\""), response.body())
		assertTrue(!response.body().contains("Exception"), response.body())
	}
}
