package com.axzydev.stock.api.core

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.idempotency.IdempotencyKey
import com.axzydev.stock.api.core.idempotency.IdempotencyService
import com.axzydev.stock.api.core.idempotency.StoredResponse
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.Public
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

// Test-only write endpoints for F0-07 and F0-08. They live in the test sources and never ship with the application.
@RestController
@RequestMapping("/api/v1/_probe")
class ProbeWriteEndpoints(
	private val idempotency: IdempotencyService,
	private val audit: AuditService,
) {

	private val counter = AtomicInteger(0)

	@PostMapping("/idempotent")
	@Public
	fun idempotent(
		@RequestHeader("Idempotency-Key") key: String,
		@RequestBody body: String,
	): ResponseEntity<String> {
		require(IdempotencyKey.isValid(key))
		val hash = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
			.joinToString("") { "%02x".format(it) }
		val stored = idempotency.execute(key, hash) {
			StoredResponse(201, "{\"sequence\":${counter.incrementAndGet()}}")
		}
		return ResponseEntity.status(stored.status).body(stored.body)
	}

	@PostMapping("/write")
	@Public
	fun write(): String {
		audit.record(Actor(null, "probe"), "PROBE_WRITE", "probe", "probe-1")
		return "{\"ok\":true}"
	}
}
