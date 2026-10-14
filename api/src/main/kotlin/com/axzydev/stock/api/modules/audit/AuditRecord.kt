package com.axzydev.stock.api.modules.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

// One row of the audit log. Rows are only added: the database rejects an update or a delete.
@Entity
@Table(name = "audit_log")
class AuditRecord(
	var occurredAt: Instant,
	var actorId: UUID?,
	var actor: String,
	var action: String,
	var targetType: String,
	var targetId: String,
	// JSON text, stored as jsonb.
	@Column(columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	var before: String?,
	@Column(columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	var after: String?,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

interface AuditRecordRepository : JpaRepository<AuditRecord, UUID>, JpaSpecificationExecutor<AuditRecord>
