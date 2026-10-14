package com.axzydev.stock.api.modules.catalogs

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "catalog")
class Catalog(
	var code: String,
	// True for a catalog the code depends on, such as INVOICE_STATUS.
	var isSystem: Boolean,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

@Embeddable
class CatalogLabel(
	var label: String = "",
	var description: String? = null,
)

@Entity
@Table(name = "catalog_item")
class CatalogItem(
	var catalogId: UUID,
	var code: String,
	var sortOrder: Int,
	var active: Boolean,
	// Typed flags as JSON text, stored as jsonb. The keys are in CatalogRules.
	@Column(columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	var attributes: String,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	// One label per language.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "catalog_item_label", joinColumns = [JoinColumn(name = "catalog_item_id")])
	@MapKeyColumn(name = "locale")
	var labels: MutableMap<String, CatalogLabel> = mutableMapOf()
}

interface CatalogRepository : JpaRepository<Catalog, UUID> {
	fun findByCode(code: String): Catalog?
}

interface CatalogItemRepository : JpaRepository<CatalogItem, UUID> {
	fun findByCatalogId(catalogId: UUID): List<CatalogItem>

	fun findByCatalogIdAndCode(catalogId: UUID, code: String): CatalogItem?

	fun countByCatalogId(catalogId: UUID): Long
}
