package com.axzydev.stock.api.modules.audit

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

// F1-17 · e2e: the audit log records every write and every denial, and nothing can change it.
class AuditLogEndpointTest : ApiTest() {

	@Autowired
	lateinit var jdbc: JdbcTemplate

	@Test
	fun `a write creates one record, with who did it, on what, and the values`() {
		val created = api.createUser("OFFICE")
		val id = created.json.get("user").get("id").asString()
		val log = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("targetType" to "user", "targetId" to id)))
		assertEquals(200, log.status)
		assertEquals(1, log.json.get("total").asInt())
		val record = log.json.get("data").get(0)
		assertEquals("USER_CREATED", record.get("action").asString())
		assertEquals("admin", record.get("actor").asString())
		assertTrue(record.get("actorId").asString().isNotBlank())
		assertTrue(record.get("before").isNull)
		assertEquals(listOf("OFFICE"), record.get("after").get("roles").list.map { it.asString() })
		assertTrue(Instant.parse(record.get("occurredAt").asString()).isAfter(Instant.now().minusSeconds(60)))
		// The record never holds a password or its hash.
		assertFalse(log.body.contains("password", ignoreCase = true), log.body)
		assertFalse(log.body.contains(created.text("temporaryPassword")))
	}

	@Test
	fun `a role change is recorded with the roles before and after`() {
		val user = api.newUser("OFFICE")
		api.put("/users/${user.id}/roles", api.adminToken(), mapOf("roles" to listOf("OFFICE", "APP_USER")))
		val log = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "USER_ROLES_CHANGED", "targetId" to user.id)))
		assertEquals(1, log.json.get("total").asInt())
		assertEquals(listOf("OFFICE"), log.json.get("data").get(0).get("before").get("roles").list.map { it.asString() })
		assertEquals(listOf("APP_USER", "OFFICE"), log.json.get("data").get(0).get("after").get("roles").list.map { it.asString() })
	}

	@Test
	fun `the log filters by date range and refuses a range that is inverted`() {
		val admin = api.adminToken()
		// Only this test's own record counts: other tests move the test clock and write records at other times.
		val id = api.createUser("OFFICE").json.get("user").get("id").asString()
		val between = { from: Instant?, to: Instant? ->
			api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetId" to id, "occurredAt" to listOf(from?.toString(), to?.toString())))).json.get("total").asInt()
		}
		assertEquals(1, between(Instant.now().minusSeconds(3600), null))
		assertEquals(0, between(Instant.now().plusSeconds(3600), null))
		assertEquals(0, between(null, Instant.now().minusSeconds(3600)))
		val inverted = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("occurredAt" to listOf(Instant.now().toString(), Instant.now().minusSeconds(60).toString()))))
		assertEquals("FORMAT_INVALID", inverted.code)
	}

	@Test
	fun `only who holds audit view reads the log`() {
		val office = api.newUser("OFFICE")
		assertEquals(403, api.post("/audit-log/query", office.token, mapOf<String, Any>()).status)
	}

	@Test
	fun `the API offers no way to change or delete an audit record`() {
		val admin = api.adminToken()
		for (method in listOf("PUT", "PATCH", "DELETE")) {
			val response = api.call(method, "/audit-log/query", admin, mapOf<String, Any>())
			assertEquals(405, response.status, method)
			assertEquals("METHOD_NOT_ALLOWED", response.code)
		}
		val id = api.post("/audit-log/query", admin, mapOf("limit" to 1)).json.get("data").get(0).get("id").asString()
		assertEquals(404, api.delete("/audit-log/$id", admin).status)
		assertEquals("NOT_FOUND", api.delete("/audit-log/$id", admin).code)
	}

	@Test
	fun `the database rejects an update, a delete and a truncate of the log`() {
		assertTrue(jdbc.queryForObject("select count(*) from audit_log", Int::class.java)!! > 0)
		assertThrows(Exception::class.java) { jdbc.update("update audit_log set actor = 'someone else'") }
		assertThrows(Exception::class.java) { jdbc.update("delete from audit_log") }
		assertThrows(Exception::class.java) { jdbc.execute("truncate audit_log") }
	}
}
