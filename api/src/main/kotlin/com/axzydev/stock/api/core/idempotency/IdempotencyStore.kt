package com.axzydev.stock.api.core.idempotency

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class IdempotencyStore(private val jdbc: JdbcTemplate) {

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
