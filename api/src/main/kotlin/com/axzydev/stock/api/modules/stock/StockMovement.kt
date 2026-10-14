package com.axzydev.stock.api.modules.stock

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// A movement is written once and never changed: the database rejects an update or a delete.
// It is always new to JPA, so saving it is one insert, with no read first.
@Entity
@Table(name = "stock_movements")
class StockMovement(
	@Id @Column(name = "id") private val movementId: UUID,
	val typeId: UUID,
	val warehouseId: UUID,
	val locationId: UUID,
	val productId: UUID,
	val quantity: BigDecimal,
	val userId: UUID?,
	val occurredAt: Instant,
	val reason: String?,
	val sourceType: String,
	val sourceId: String,
) : Persistable<UUID> {
	// The order of the movements, given by the database.
	@Generated(event = [EventType.INSERT])
	@Column(insertable = false, updatable = false)
	var seq: Long? = null

	override fun getId(): UUID = movementId

	override fun isNew(): Boolean = true
}

interface StockMovementRepository : JpaRepository<StockMovement, UUID> {
	fun existsBySourceTypeAndWarehouseId(sourceType: String, warehouseId: UUID): Boolean
}
