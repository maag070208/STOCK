package com.axzydev.stock.data.health

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

// F0-13 · unit: the health client maps the API answer to a state, and an unreachable API is not a crash.
class HealthClientTest {

	private fun clientReplying(status: HttpStatusCode, body: String) =
		HealthClient(
			engine = MockEngine {
				respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
			},
			baseUrl = "http://test/api/v1",
		)

	@Test
	fun `a 200 answer with the database up is Up`() = runTest {
		val state = clientReplying(HttpStatusCode.OK, """{"status":"UP","database":"UP"}""").check()
		assertEquals(HealthState.Up, state)
	}

	@Test
	fun `a 503 answer is Down`() = runTest {
		val state = clientReplying(HttpStatusCode.ServiceUnavailable, """{"status":"DOWN","database":"DOWN"}""").check()
		assertEquals(HealthState.Down, state)
	}

	@Test
	fun `a failed connection is Unreachable and not a crash`() = runTest {
		val client = HealthClient(
			engine = MockEngine { throw IOException("connection refused") },
			baseUrl = "http://test/api/v1",
		)
		assertEquals(HealthState.Unreachable, client.check())
	}
}
