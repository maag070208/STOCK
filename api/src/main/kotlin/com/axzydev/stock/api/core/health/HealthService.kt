package com.axzydev.stock.api.core.health

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class HealthService(private val jdbc: JdbcTemplate) {

	fun databaseUp(): Boolean =
		try {
			jdbc.queryForObject("select 1", Int::class.java) == 1
		} catch (e: DataAccessException) {
			false
		}

	// Ready means the database answers and the Flyway history records at least one applied migration.
	fun ready(): Boolean = databaseUp() && migrationsApplied()

	private fun migrationsApplied(): Boolean =
		try {
			(jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success = true",
				Int::class.java,
			) ?: 0) > 0
		} catch (e: DataAccessException) {
			false
		}
}
