package com.axzydev.stock.api.db

import com.axzydev.stock.api.TestcontainersConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

// F0-02 · e2e: starting the API on an empty database applies V1 and records it.
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class FlywayStartupTest {

	@Autowired
	lateinit var jdbc: JdbcTemplate


	@Test
	fun `startup applies V1 and records it in the history`() {
		val applied = jdbc.queryForObject(
			"select count(*) from flyway_schema_history where success = true and version = '1'",
			Int::class.java,
		)
		assertTrue(applied == 1, "V1 must be applied once, was $applied")
	}
}
