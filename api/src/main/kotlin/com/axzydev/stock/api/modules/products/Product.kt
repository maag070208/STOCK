package com.axzydev.stock.api.modules.products

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
class Product(
	var code: String,
	var name: String,
	var unitId: UUID,
	var barcode: String,
	var legacyId: Int?,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	// The minimum stock of the material, in its unit: below it, it shows up in the report of what to buy (reports.md
	// section 5). Zero means no minimum was set.
	var minimum: java.math.BigDecimal = java.math.BigDecimal.ZERO

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

interface ProductRepository : JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
	fun findByCode(code: String): Product?

	fun findByCodeIn(codes: Collection<String>): List<Product>

	fun existsByBarcodeAndActiveTrueAndIdNot(barcode: String, id: UUID): Boolean

	fun existsByBarcodeAndActiveTrue(barcode: String): Boolean

	fun findByBarcodeAndActiveTrue(barcode: String): Product?

	fun findByBarcodeInAndActiveTrue(barcodes: Collection<String>): List<Product>

	fun findByLegacyIdIn(legacyIds: Collection<Int>): List<Product>
}
