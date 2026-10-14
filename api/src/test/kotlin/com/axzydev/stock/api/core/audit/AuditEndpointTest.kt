package com.axzydev.stock.api.core.audit

import com.axzydev.stock.api.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// F0-08 · e2e: a write through the API reaches the audit port (the test recorder replaces the no-op adapter).
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class AuditEndpointTest {

	@LocalServerPort
	var port: Int = 0

	@Autowired
	lateinit var recorder: RecordingAuditPort

	@Test
	fun `a write endpoint records one audit event`() {
		recorder.clear()
		val response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/_probe/write"))
				.POST(HttpRequest.BodyPublishers.noBody())
				.build(),
			HttpResponse.BodyHandlers.ofString(),
		)
		assertEquals(200, response.statusCode())
		assertEquals(listOf("PROBE_WRITE"), recorder.events.map { it.action })
	}
}
