package com.axzydev.stock.api.core.errors

import com.axzydev.stock.api.core.security.Public
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Test-only endpoints. They live in the test sources, so they never ship with the application.
data class EchoBody(val text: String)

@RestController
@RequestMapping("/api/v1/_probe")
class ProbeController {

	@PostMapping("/echo")
	@Public
	fun echo(@RequestBody body: EchoBody): EchoBody = body

	@GetMapping("/boom")
	@Public
	fun boom(): Unit = throw RuntimeException("internal detail that must not leak")
}
