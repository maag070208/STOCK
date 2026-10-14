package com.axzydev.stock.api.modules.orders

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// An order takes materials out of one warehouse (order-fulfillment.md section 3). Its number is unique per warehouse.
@Entity
@Table(name = "orders")
class Order(
	var number: String,
	var warehouseId: UUID,
	var customerId: UUID,
	var contactId: UUID?,
	var dueDate: LocalDate,
	var statusId: UUID,
	var operatorId: UUID?,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var validatedAt: Instant? = null
	var validatedBy: UUID? = null
}

// One material of an order (order-fulfillment.md section 4). Its quantity is reserved at creation, and picked falls as
// the operator takes it out, so the reservation left is quantity - pickedQuantity.
@Entity
@Table(name = "order_lines")
class OrderLine(
	var orderId: UUID,
	var productId: UUID,
	var unitId: UUID,
	var quantity: BigDecimal,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var pickedQuantity: BigDecimal = BigDecimal.ZERO

	// What the order still reserves of this line: what is not picked yet.
	val reserved: BigDecimal get() = quantity.subtract(pickedQuantity)

	val picked: Boolean get() = pickedQuantity.compareTo(quantity) == 0
}

interface OrderRepository : JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
	fun existsByWarehouseIdAndNumber(warehouseId: UUID, number: String): Boolean

	// Picks, validation and reassignment of one order run one at a time, so its status always reads what is stored.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	fun lockById(id: UUID): Order?
}

interface OrderLineRepository : JpaRepository<OrderLine, UUID> {
	fun findByOrderId(orderId: UUID): List<OrderLine>
}
