package com.axzydev.stock.api.core.idempotency

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class IdempotencyStore(private val jdbc: JdbcTemplate) {

	// One key at a time: two retries of the same request that arrive together wait for each other, so the second
	// finds the record the first wrote instead of running the action again. The lock is released with the transaction.
	fun lock(key: String) {
		jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", key)
	}

	fun find(key: String): Stored? =
		jdbc.query(
			"select request_hash, response_status, response_body from idempotency_record where idempotency_key = ?",
			{ rs, _ -> Stored(rs.getString(1), StoredResponse(rs.getInt(2), rs.getString(3))) },
			key,
		).firstOrNull()

	fun save(key: String, requestHash: String, response: StoredResponse) {
		jdbc.update(
			"insert into idempotency_record (idempotency_key, request_hash, response_status, response_body) values (?, ?, ?, ?)",
			key, requestHash, response.status, response.body,
		)
	}
}
