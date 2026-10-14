package com.axzydev.stock.api.modules.entries

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StaffKey(val warehouseId: UUID? = null, val userId: UUID? = null) : Serializable

@Entity
@Table(name = "staff_templates")
@IdClass(StaffKey::class)
class StaffMember(@Id val warehouseId: UUID, @Id val userId: UUID)

@Entity
@Table(name = "invoices")
class Invoice(
	var number: String,
	var supplierId: UUID,
	var contactId: UUID?,
	var invoiceDate: LocalDate,
	var warehouseId: UUID,
	var statusId: UUID,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

@Entity
@Table(name = "invoice_lines")
class InvoiceLine(
	var invoiceId: UUID,
	var productId: UUID,
	var unitId: UUID,
	var quantity: BigDecimal,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

@Entity
@Table(name = "receipt_assignments")
class ReceiptAssignment(
	var lineId: UUID,
	var operatorId: UUID,
	var expectedQuantity: BigDecimal,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var countedQuantity: BigDecimal? = null
	var countNotes: String? = null
	var countedAt: Instant? = null
	var placedQuantity: BigDecimal = BigDecimal.ZERO

	val counted: Boolean get() = countedQuantity != null

	// Counted and equal to what was expected: a match, or a discrepancy that was adjusted.
	val settled: Boolean get() = countedQuantity?.compareTo(expectedQuantity) == 0

	val placed: Boolean get() = countedQuantity != null && placedQuantity.compareTo(countedQuantity) == 0
}

@Entity
@Table(name = "receipt_placements")
class ReceiptPlacement(
	var assignmentId: UUID,
	var locationId: UUID,
	var quantity: BigDecimal,
	var movementId: UUID,
	var placedAt: Instant,
	var placedBy: UUID?,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

interface StaffMemberRepository : JpaRepository<StaffMember, StaffKey> {
	fun findByWarehouseId(warehouseId: UUID): List<StaffMember>

	fun deleteByWarehouseId(warehouseId: UUID)
}

interface InvoiceRepository : JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
	fun existsBySupplierIdAndNumber(supplierId: UUID, number: String): Boolean

	// Counts, adjustments and placements of one invoice run one at a time, so its status always reads what is stored.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Invoice i where i.id = :id")
	fun lockById(id: UUID): Invoice?
}

interface InvoiceLineRepository : JpaRepository<InvoiceLine, UUID> {
	fun findByInvoiceId(invoiceId: UUID): List<InvoiceLine>
}

interface ReceiptAssignmentRepository : JpaRepository<ReceiptAssignment, UUID> {
	fun findByLineIdIn(lineIds: Collection<UUID>): List<ReceiptAssignment>

	fun findByOperatorId(operatorId: UUID): List<ReceiptAssignment>

	// The invoice of an assignment, as a value: nothing is loaded before the invoice is locked.
	@Query("select l.invoiceId from InvoiceLine l where l.id = (select a.lineId from ReceiptAssignment a where a.id = :assignmentId)")
	fun invoiceIdOf(assignmentId: UUID): UUID?

	// The open discrepancies: counted, and different from what was expected.
	@Query(
		"""
		select a from ReceiptAssignment a where a.countedQuantity is not null and a.countedQuantity <> a.expectedQuantity
		and a.lineId in (select l.id from InvoiceLine l where l.invoiceId in (select i.id from Invoice i where i.warehouseId in :warehouses))
		""",
	)
	fun openDiscrepancies(warehouses: Collection<UUID>): List<ReceiptAssignment>
}

interface ReceiptPlacementRepository : JpaRepository<ReceiptPlacement, UUID> {
	fun findByAssignmentIdIn(assignmentIds: Collection<UUID>): List<ReceiptPlacement>
}
