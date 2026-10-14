package com.axzydev.stock.data.health

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

sealed interface HealthState {
	data object Up : HealthState
	data object Down : HealthState
	data object Unreachable : HealthState
}

// Asks the API whether it is up. A failed connection is a state, not an exception.
class HealthClient(engine: HttpClientEngine, private val baseUrl: String) {

	private val http = HttpClient(engine)

	suspend fun check(): HealthState =
		try {
			val response: HttpResponse = http.get("$baseUrl/health")
			if (response.status == HttpStatusCode.OK) HealthState.Up else HealthState.Down
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			HealthState.Unreachable
		}
}
