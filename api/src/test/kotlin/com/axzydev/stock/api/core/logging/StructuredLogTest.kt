package com.axzydev.stock.api.core.logging

import com.axzydev.stock.api.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper

// F0 · the API writes structured logs: one JSON object per line (api.md section 1).
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class StructuredLogTest {

	@Test
	fun `a log line is one JSON object with its time, level and message`(output: CapturedOutput) {
		LoggerFactory.getLogger("stockflow.probe").info("structured log probe")
		val line = output.out.lines().last { it.contains("structured log probe") }
		val json = JsonMapper.builder().build().readTree(line)
		assertEquals("structured log probe", json.get("message").asString())
		assertEquals("INFO", json.get("log").get("level").asString())
		assertTrue(json.has("@timestamp"), line)
	}
}
