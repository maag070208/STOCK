package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TableFilter
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.customers.CustomerService
import com.axzydev.stock.api.modules.orders.OrderRules.Status
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.StockProduct
import com.axzydev.stock.api.modules.stock.StockRules
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class OrderLineBody(val productId: UUID? = null, val quantity: BigDecimal? = null)

data class CreateOrderRequest(
	val number: String? = null,
	val warehouseId: UUID? = null,
	val customerId: UUID? = null,
	val contactId: UUID? = null,
	val dueDate: LocalDate? = null,
	val lines: List<OrderLineBody>? = null,
)

// Orders (order-fulfillment.md): creation with the availability check and reservation, the reads, and later the
// picking, status machine, validation, cancellation and reassignment.
@Service
class OrderService(
	private val orders: OrderRepository,
	private val lines: OrderLineRepository,
	private val customers: CustomerService,
	private val warehouses: WarehouseService,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val views: OrderViews,
	private val audit: AuditService,
	private val jdbc: JdbcTemplate,
	private val clock: Clock,
) {
	private val statusFilter: TableFilter<Order> = { root, builder, value ->
		when (val id = catalogs.itemId(STATUS_CATALOG, value.toString())) {
			null -> builder.disjunction()
			else -> builder.equal(root.get<UUID>("statusId"), id)
		}
	}

	private val table = TableSpec<Order>(
		filters = mapOf(
			"number" to TableSpec.contains("number"),
			"warehouseId" to TableSpec.id("warehouseId"),
			"customerId" to TableSpec.id("customerId"),
			"operatorId" to TableSpec.id("operatorId"),
			"status" to statusFilter,
		),
		sorts = mapOf("number" to "number", "dueDate" to "dueDate", "createdAt" to "createdAt"),
		defaultSort = Sort.by(Sort.Direction.DESC, "createdAt"),
	)

	// With AREA, the orders of the reader's warehouses only.
	@Transactional(readOnly = true)
	fun query(user: CurrentUser, request: TableRequest, language: String): TablePage<OrderView> {
		val scope = if (user.scopeOf(VIEW) == Scope.ALL) null else Specification<Order> { root, _, builder ->
			if (user.warehouseIds.isEmpty()) builder.disjunction() else root.get<UUID>("warehouseId").`in`(user.warehouseIds)
		}
		return orders.table(request, table, scope) { it }.mapRows { rows -> views.headers(rows, language) }
	}

	@Transactional(readOnly = true)
	fun get(user: CurrentUser, id: UUID, language: String): OrderView = views.full(readable(user, id), language)

	@Transactional
	fun create(user: CurrentUser, request: CreateOrderRequest, language: String): OrderView {
		val check = Validator()
		val number = check.required("number", request.number, OrderRules::number) { it.uppercase() }
		customers.requireForNewRecord(check, request.customerId, request.contactId)
		when (val date = request.dueDate) {
			null -> check.reject("dueDate", ErrorCode.FIELD_REQUIRED)
			else -> OrderRules.dueDate(date, today())?.let { check.reject("dueDate", it) }
		}
		warehouses.requireActive(check, "warehouseId", request.warehouseId)
		val bodies = request.lines.orEmpty()
		val found = products.stockProducts(bodies.mapNotNull { it.productId })
		val seen = mutableSetOf<UUID>()
		bodies.forEachIndexed { i, line ->
			val product = line.productId?.let(found::get)
			when {
				line.productId == null -> check.reject("lines.$i.productId", ErrorCode.FIELD_REQUIRED)
				product == null || !product.active -> check.reject("lines.$i.productId", ErrorCode.CATALOG_VALUE_INVALID)
				!seen.add(line.productId) -> check.reject("lines.$i.productId", ErrorCode.DUPLICATE_VALUE)
			}
			quantity(check, "lines.$i.quantity", line.quantity, product)
		}
		check.check()
		if (orders.existsByWarehouseIdAndNumber(request.warehouseId!!, number)) throw ValidationException(listOf(FieldError("number", ErrorCode.DUPLICATE_VALUE)))

		// Each line reserves its quantity under a lock on its material and warehouse, in a fixed order, so two orders
		// for the last unit cannot both take it (order-fulfillment.md section 5).
		val ordered = bodies.sortedBy { it.productId.toString() }
		for (body in ordered) reserve(body.productId!!, request.warehouseId!!, body.quantity!!)

		val now = clock.instant()
		val order = orders.save(Order(number, request.warehouseId!!, request.customerId!!, request.contactId, request.dueDate!!, statusId(Status.CREATED), null, user.id, now, now))
		for (body in bodies) {
			lines.save(OrderLine(order.id!!, body.productId!!, found.getValue(body.productId).unitId, body.quantity!!))
		}
		audit.record(user.actor, "ORDER_CREATED", "order", order.id.toString(), after = mapOf("number" to number, "lines" to bodies.size))
		return views.full(order, language)
	}

	// For other modules: an order the person may read, by id.
	fun readable(user: CurrentUser, id: UUID): Order {
		val order = orders.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		user.requireRead(VIEW, order.warehouseId)
		return order
	}

	fun status(order: Order): Status = Status.valueOf(catalogs.itemViews(listOf(order.statusId), "es").getValue(order.statusId).code)

	fun statusId(status: Status): UUID = catalogs.itemId(STATUS_CATALOG, status.name) ?: error("ORDER_STATUS has no ${status.name}")

	// The reservation of one line: available = the warehouse balance minus what open orders have reserved, checked and
	// taken inside the advisory lock on the material and warehouse. A line above what is available is refused, and the
	// message says how much is available.
	private fun reserve(productId: UUID, warehouseId: UUID, quantity: BigDecimal) {
		jdbc.query("select pg_advisory_xact_lock(?, ?)", { _ -> }, warehouseId.hashCode(), productId.hashCode())
		val available = balance(productId, warehouseId).subtract(reserved(productId, warehouseId))
		if (quantity > available) throw ApiException(ErrorCode.STOCK_INSUFFICIENT, available.stripTrailingZeros().toPlainString())
	}

	private fun balance(productId: UUID, warehouseId: UUID): BigDecimal =
		jdbc.queryForObject(
			"select coalesce(sum(quantity), 0) from stock_balances where product_id = ? and warehouse_id = ?",
			BigDecimal::class.java, productId, warehouseId,
		)!!

	// What open orders of the warehouse still reserve of a material: the unpicked part of every line of an order that
	// is not yet validated or cancelled.
	private fun reserved(productId: UUID, warehouseId: UUID): BigDecimal =
		jdbc.queryForObject(
			"""
			select coalesce(sum(l.quantity - l.picked_quantity), 0)
			from order_lines l join orders o on o.id = l.order_id
			where l.product_id = ? and o.warehouse_id = ? and o.status_id not in (?, ?)
			""".trimIndent(),
			BigDecimal::class.java, productId, warehouseId, statusId(Status.VALIDATED), statusId(Status.CANCELLED),
		)!!

	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(BUSINESS_ZONE))

	private fun quantity(check: Validator, field: String, value: BigDecimal?, product: StockProduct?) {
		when {
			value == null -> check.reject(field, ErrorCode.FIELD_REQUIRED)
			else -> StockRules.quantity(value, product?.decimals ?: 0)?.let { check.reject(field, it) }
		}
	}

	companion object {
		const val VIEW = "orders.view"
		const val CREATE = "orders.create"
		const val ASSIGN = "orders.assign"
		const val FULFILL = "orders.fulfill"
		const val VALIDATE = "orders.validate"
		const val CANCEL = "orders.cancel"
		const val REASSIGN = "orders.reassign"
		const val ACCEPT_DIFFERENCE = "orders.accept_difference"
		const val STATUS_CATALOG = "ORDER_STATUS"
		const val BUSINESS_ZONE = "America/Mexico_City"
	}
}
