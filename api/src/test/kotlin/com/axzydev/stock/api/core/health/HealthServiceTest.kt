package com.axzydev.stock.api.core.health

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate

// F0-03 · unit: the health service reports DOWN when the database check fails.
class HealthServiceTest {

	@Test
	fun `database is UP when the check query answers`() {
		val jdbc = mockk<JdbcTemplate>()
		every { jdbc.queryForObject("select 1", Int::class.java) } returns 1
		assertTrue(HealthService(jdbc).databaseUp())
	}

	@Test
	fun `database is DOWN when the check query fails`() {
		val jdbc = mockk<JdbcTemplate>()
		every { jdbc.queryForObject("select 1", Int::class.java) } throws
			DataAccessResourceFailureException("connection refused")
		assertFalse(HealthService(jdbc).databaseUp())
	}

	@Test
	fun `not ready when the migration history is missing`() {
		val jdbc = mockk<JdbcTemplate>()
		every { jdbc.queryForObject("select 1", Int::class.java) } returns 1
		every {
			jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success = true",
				Int::class.java,
			)
		} throws DataAccessResourceFailureException("relation does not exist")
		assertFalse(HealthService(jdbc).ready())
	}
}
