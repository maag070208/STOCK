package com.axzydev.stock.api.modules.labels

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "label_templates")
class LabelTemplate(
	var key: String,
	var nameEs: String,
	var nameEn: String,
	var version: Int,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true

	// The design of the editor: the blocks, as JSON. Nullable: a template written by hand has none (labels.md section 4).
	// It is a `jsonb` column, so Hibernate has to be told the string is JSON and not a varchar.
	@JdbcTypeCode(SqlTypes.JSON)
	var design: String? = null
}

data class TemplateVersionKey(val templateId: UUID? = null, val version: Int? = null) : Serializable

@Entity
@Table(name = "label_template_versions")
@IdClass(TemplateVersionKey::class)
class LabelTemplateVersion(
	@Id val templateId: UUID,
	@Id val version: Int,
	val zpl: String,
	val createdAt: Instant,
	val createdBy: UUID?,
)

@Entity
@Table(name = "printers")
class Printer(
	var name: String,
	var warehouseId: UUID,
	var ipAddress: String,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

interface LabelTemplateRepository : JpaRepository<LabelTemplate, UUID> {
	fun findByKey(key: String): LabelTemplate?
}

interface LabelTemplateVersionRepository : JpaRepository<LabelTemplateVersion, TemplateVersionKey> {
	fun findByTemplateIdOrderByVersionDesc(templateId: UUID): List<LabelTemplateVersion>

	fun findByTemplateIdAndVersion(templateId: UUID, version: Int): LabelTemplateVersion?
}

interface PrinterRepository : JpaRepository<Printer, UUID> {
	fun findByWarehouseIdIn(warehouseIds: Collection<UUID>): List<Printer>
}
