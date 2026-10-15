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

	// The orders whose due date has come, or is tomorrow: the ones that raise an alert on this day (section 7a).
	@Query("select o from Order o where o.dueDate <= :last and o.statusId not in :excluded order by o.dueDate")
	fun findDueBy(last: LocalDate, excluded: Collection<UUID>): List<Order>

	// Picks, validation and reassignment of one order run one at a time, so its status always reads what is stored.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	fun lockById(id: UUID): Order?
}

interface OrderLineRepository : JpaRepository<OrderLine, UUID> {
	fun findByOrderId(orderId: UUID): List<OrderLine>
}

// One pick of a line: how much of it left one location, and the OUT movement that moved it.
@Entity
@Table(name = "order_picks")
class OrderPick(
	var lineId: UUID,
	var locationId: UUID,
	var quantity: BigDecimal,
	var movementId: UUID,
	var pickedBy: UUID?,
	var pickedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

interface OrderPickRepository : JpaRepository<OrderPick, UUID> {
	fun findByLineIdIn(lineIds: Collection<UUID>): List<OrderPick>
}

// One alert of one order to one person on one day (order-fulfillment.md section 7a). The unique key on the three is what
// stops the same day from being raised twice.
@Entity
@Table(name = "order_alerts")
class OrderAlert(
	var orderId: UUID,
	var userId: UUID,
	var alertDate: LocalDate,
	var dueDate: LocalDate,
	var kind: String,
	var createdAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

interface OrderAlertRepository : JpaRepository<OrderAlert, UUID> {
	fun findByUserIdOrderByAlertDateDescDueDateAsc(userId: UUID): List<OrderAlert>
}

// The comprobante of a validated order (order-fulfillment.md section 10): one per order, built and written once.
@Entity
@Table(name = "order_documents")
class OrderDocument(
	@Id var orderId: UUID,
	var content: ByteArray,
	var contentType: String,
	var fileName: String,
	var createdAt: Instant,
)

interface OrderDocumentRepository : JpaRepository<OrderDocument, UUID>

// A reassignment of picked material (order-fulfillment.md section 8): what left one order for another, or went back to a
// location. The picks themselves change line, so this is what the reports count as moved.
@Entity
@Table(name = "order_reassignments")
class OrderReassignment(
	var orderId: UUID,
	var lineId: UUID,
	var targetOrderId: UUID?,
	var targetLineId: UUID?,
	var locationId: UUID?,
	var kind: String,
	var quantity: BigDecimal,
	var note: String,
	var userId: UUID?,
	var createdAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null
}

interface OrderReassignmentRepository : JpaRepository<OrderReassignment, UUID>
