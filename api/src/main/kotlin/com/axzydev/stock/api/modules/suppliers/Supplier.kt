package com.axzydev.stock.api.modules.suppliers

import com.axzydev.stock.api.modules.partners.Address
import jakarta.persistence.Embedded
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
@Table(name = "suppliers")
class Supplier(
	var code: String,
	var name: String,
	var taxId: String,
	@Embedded var address: Address,
	var paymentTermsId: UUID,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

@Entity
@Table(name = "supplier_contacts")
class SupplierContact(
	var supplierId: UUID,
	var name: String,
	var email: String?,
	var phone: String?,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

interface SupplierRepository : JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {
	fun findByCode(code: String): Supplier?
}

interface SupplierContactRepository : JpaRepository<SupplierContact, UUID> {
	fun findBySupplierIdOrderByName(supplierId: UUID): List<SupplierContact>

	fun findByIdAndSupplierId(id: UUID, supplierId: UUID): SupplierContact?
}
