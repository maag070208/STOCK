package com.axzydev.stock.api.core.health

import com.axzydev.stock.api.core.security.Public
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class HealthResponse(val status: String, val database: String)
data class ReadyResponse(val status: String)

@RestController
@RequestMapping("/api/v1/health")
class HealthController(private val health: HealthService) {

	@GetMapping
	@Public
	fun health(): ResponseEntity<HealthResponse> {
		val up = health.databaseUp()
		val body = HealthResponse(status = if (up) "UP" else "DOWN", database = if (up) "UP" else "DOWN")
		return ResponseEntity.status(if (up) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE).body(body)
	}

	@GetMapping("/ready")
	@Public
	fun ready(): ResponseEntity<ReadyResponse> {
		val ready = health.ready()
		val body = ReadyResponse(status = if (ready) "READY" else "NOT_READY")
		return ResponseEntity.status(if (ready) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE).body(body)
	}
}
