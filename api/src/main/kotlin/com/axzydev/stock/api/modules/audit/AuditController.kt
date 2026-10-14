package com.axzydev.stock.api.modules.audit

import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

data class AuditRecordView(
	val id: UUID,
	val occurredAt: Instant,
	val actorId: UUID?,
	val actor: String,
	val action: String,
	val targetType: String,
	val targetId: String,
	val before: JsonNode?,
	val after: JsonNode?,
)

// The audit log is read-only through the API: there is no endpoint that changes or removes a record.
@RestController
@RequestMapping("/api/v1/audit-log")
class AuditController(private val records: AuditRecordRepository, private val json: JsonMapper) {

	private val spec = TableSpec<AuditRecord>(
		filters = mapOf(
			"action" to TableSpec.equalTo("action"),
			"actor" to TableSpec.contains("actor"),
			"targetType" to TableSpec.equalTo("targetType"),
			"targetId" to TableSpec.equalTo("targetId"),
			"occurredAt" to TableSpec.between("occurredAt"),
		),
		sorts = mapOf("occurredAt" to "occurredAt", "actor" to "actor", "action" to "action", "targetType" to "targetType", "targetId" to "targetId"),
		defaultSort = Sort.by(Sort.Direction.DESC, "occurredAt"),
	)

	@PostMapping("/query")
	@RequiresPermission("audit.view")
	@Transactional(readOnly = true)
	fun query(@RequestBody request: TableRequest): TablePage<AuditRecordView> =
		records.table(request, spec) {
			AuditRecordView(it.id!!, it.occurredAt, it.actorId, it.actor, it.action, it.targetType, it.targetId, it.before?.let(json::readTree), it.after?.let(json::readTree))
		}
}
