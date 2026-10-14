package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TableFilter
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.customers.CustomerService
import com.axzydev.stock.api.modules.entries.StaffTemplateService
import com.axzydev.stock.api.modules.orders.OrderRules.Status
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.StockProduct
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockRules
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.modules.warehouses.LocationService
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

data class AssignRequest(val operatorId: UUID? = null)

data class PickBody(val lineId: UUID? = null, val locationId: UUID? = null, val quantity: BigDecimal? = null)

// A note the manager writes, required when a difference is accepted and when validation returns an order.
data class NoteRequest(val note: String? = null)

// Reassignment (order-fulfillment.md section 8): each of the three actions takes a note.
data class ReassignOperatorRequest(val operatorId: UUID? = null, val note: String? = null)

data class TransferPicksRequest(val lineId: UUID? = null, val targetOrderId: UUID? = null, val quantity: BigDecimal? = null, val note: String? = null)

data class ReturnPicksRequest(val lineId: UUID? = null, val locationId: UUID? = null, val quantity: BigDecimal? = null, val note: String? = null)

// Orders (order-fulfillment.md): creation with the availability check and reservation, the reads, and later the
// picking, status machine, validation, cancellation and reassignment.
@Service
class OrderService(
	private val orders: OrderRepository,
	private val lines: OrderLineRepository,
	private val picks: OrderPickRepository,
	private val customers: CustomerService,
	private val warehouses: WarehouseService,
	private val staff: StaffTemplateService,
	private val locations: LocationService,
	private val stock: StockService,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val views: OrderViews,
	private val documents: OrderDocumentService,
	private val audit: AuditService,
	private val jdbc: JdbcTemplate,
	private val clock: Clock,
) {
	private val statusFilter: TableFilter<Order> = { root, builder, value ->
		// One status code, or several: the app asks for the open orders of a warehouse in one query.
		val codes = (value as? List<*>)?.map { it.toString() } ?: listOf(value.toString())
		val ids = codes.mapNotNull { catalogs.itemId(STATUS_CATALOG, it) }
		if (ids.isEmpty() || ids.size != codes.size) builder.disjunction() else root.get<UUID>("statusId").`in`(ids)
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

	// The order goes to one active operator of its warehouse (order-fulfillment.md section 7a). Assigning changes no stock.
	@Transactional
	fun assign(user: CurrentUser, orderId: UUID, request: AssignRequest, language: String): OrderView {
		val order = readable(user, orderId)
		user.requireWrite(ASSIGN, order.warehouseId)
		requireStatus(order, Status.CREATED, Status.FULFILLING)
		val check = Validator()
		operator(check, order.warehouseId, request.operatorId)
		check.check()
		val before = order.operatorId
		order.operatorId = request.operatorId
		touch(order)
		audit.record(user.actor, "ORDER_ASSIGNED", "order", order.id.toString(), mapOf("operatorId" to before), mapOf("operatorId" to order.operatorId))
		return views.full(order, language)
	}

	// A pick takes material out of one location (order-fulfillment.md section 7). The operator chooses the location; the
	// system never suggests one. The pick is an OUT movement, and it reduces the reservation and the balance together.
	@Transactional
	fun pick(user: CurrentUser, orderId: UUID, request: PickBody, language: String): OrderView {
		val order = orders.lockById(orderId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		user.requireRead(FULFILL, order.warehouseId)
		requireStatus(order, Status.CREATED, Status.FULFILLING)
		val check = Validator()
		val line = lineOf(check, order, request.lineId)
		val product = line?.let { products.stockProducts(listOf(it.productId))[it.productId] }
		val target = request.locationId?.let { locations.stockTargets(listOf(it))[it] }
		when {
			request.locationId == null -> check.reject("locationId", ErrorCode.FIELD_REQUIRED)
			target == null -> check.reject("locationId", ErrorCode.CATALOG_VALUE_INVALID)
			target.warehouseId != order.warehouseId -> check.reject("locationId", ErrorCode.LOCATION_NOT_IN_WAREHOUSE)
		}
		quantity(check, "quantity", request.quantity, product)
		check.check()
		// A pick cannot exceed the line's remaining quantity; the OUT movement then refuses one above the location's balance.
		if (line!!.pickedQuantity.add(request.quantity!!) > line.quantity) throw ApiException(ErrorCode.PICK_EXCEEDS_LINE)
		val movement = stock.apply(
			user.actor,
			listOf(MovementCommand(StockService.OUT, target!!.id, line.productId, request.quantity!!, "Orden ${order.number}", SOURCE, order.id.toString())),
		).single()
		val now = clock.instant()
		picks.save(OrderPick(line.id!!, target.id, request.quantity!!, movement.id, user.id, now))
		line.pickedQuantity = line.pickedQuantity.add(request.quantity!!)
		touch(order)
		audit.record(user.actor, "ORDER_PICKED", "order", order.id.toString(), after = mapOf("lineId" to line.id.toString(), "locationId" to target.id.toString(), "quantity" to request.quantity))
		// The first pick starts fulfillment; the last one ends it.
		advance(order, user.actor)
		return views.full(order, language)
	}

	// Validation (order-fulfillment.md section 6, and the mismatch in section 10): a FULFILLED order is checked and
	// locked. A mismatch returns it to FULFILLING, with a note, so the operator corrects it.
	@Transactional
	fun validate(user: CurrentUser, orderId: UUID, request: NoteRequest, language: String): OrderView {
		val order = orders.lockById(orderId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		user.requireWrite(VALIDATE, order.warehouseId)
		requireStatus(order, Status.FULFILLED)
		val all = lines.findByOrderId(order.id!!)
		if (all.any { !it.picked }) {
			// A line short of its quantity: back to FULFILLING, with a note, so the operator corrects it.
			val note = checkNote(request.note)
			val before = status(order).name
			order.statusId = statusId(Status.FULFILLING)
			touch(order)
			audit.record(user.actor, "ORDER_STATUS_CHANGED", "order", order.id.toString(), mapOf("status" to before), mapOf("status" to Status.FULFILLING.name, "note" to note))
			return views.full(order, language)
		}
		order.validatedAt = clock.instant()
		order.validatedBy = user.id
		touch(order)
		move(user.actor, order, Status.VALIDATED)
		// The comprobante is built from the order as it stands, and kept: it is the delivery receipt (section 10).
		documents.store(order, language)
		audit.record(user.actor, "ORDER_VALIDATED", "order", order.id.toString())
		return views.full(order, language)
	}

	// Accepting a difference (order-fulfillment.md section 10): a shortage the customer accepts adjusts the line to the
	// picked quantity, the same way as in material entries. The manager or the administrator does it, with a note.
	@Transactional
	fun acceptDifference(user: CurrentUser, orderId: UUID, request: NoteRequest, language: String): OrderView {
		val order = orders.lockById(orderId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		user.requireWrite(ACCEPT_DIFFERENCE, order.warehouseId)
		requireStatus(order, Status.FULFILLING)
		val note = checkNote(request.note)
		val all = lines.findByOrderId(order.id!!)
		val short = all.filter { !it.picked }
		if (short.isEmpty()) throw ApiException(ErrorCode.ORDER_STATUS_INVALID)
		val before = short.associate { it.id.toString() to it.quantity }
		short.forEach { it.quantity = it.pickedQuantity }
		touch(order)
		audit.record(
			user.actor, "ORDER_DIFFERENCE_ACCEPTED", "order", order.id.toString(),
			before = mapOf("lines" to before, "note" to note),
			after = mapOf("lines" to short.associate { it.id.toString() to it.quantity }),
		)
		// Every line now balances, so the order is ready to validate.
		if (all.all { it.picked }) move(user.actor, order, Status.FULFILLED)
		return views.full(order, language)
	}

	// Cancellation (order-fulfillment.md section 8): only with no picks, and it releases the reservation (the cancelled
	// order no longer counts as reserved). Once material is picked, it goes through reassignment first.
	@Transactional
	fun cancel(user: CurrentUser, orderId: UUID, language: String): OrderView {
		val order = orders.lockById(orderId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		user.requireWrite(CANCEL, order.warehouseId)
		requireStatus(order, Status.CREATED, Status.FULFILLING)
		if (lines.findByOrderId(order.id!!).any { it.pickedQuantity.signum() > 0 }) throw ApiException(ErrorCode.ORDER_HAS_PICKS)
		move(user.actor, order, Status.CANCELLED)
		audit.record(user.actor, "ORDER_CANCELLED", "order", order.id.toString())
		return views.full(order, language)
	}

	// Reassignment (order-fulfillment.md section 8): an open order changes operator, its picked quantities move to
	// another open order of the same warehouse that needs them, or they return to a location of the warehouse. Each
	// action takes a note, is audited with its source, target and quantity, and is guarded by orders.reassign.
	@Transactional
	fun reassignOperator(user: CurrentUser, orderId: UUID, request: ReassignOperatorRequest, language: String): OrderView {
		val order = openForReassignment(user, orderId)
		// Reassignment moves an order that is already assigned; giving one its first operator is assignment's job.
		if (order.operatorId == null) throw ApiException(ErrorCode.ORDER_STATUS_INVALID)
		val note = checkNote(request.note)
		val check = Validator()
		operator(check, order.warehouseId, request.operatorId)
		check.check()
		val before = order.operatorId
		order.operatorId = request.operatorId
		touch(order)
		audit.record(user.actor, "ORDER_REASSIGNED", "order", order.id.toString(), mapOf("operatorId" to before), mapOf("operatorId" to order.operatorId, "note" to note))
		return views.full(order, language)
	}

	// The picked quantities of a line move to the same material of another open order. The goods are already out of the
	// shelves, so no balance changes: the picks change the line they serve, and both reservations follow the lines.
	@Transactional
	fun transferPicks(user: CurrentUser, orderId: UUID, request: TransferPicksRequest, language: String): OrderView {
		val targetId = request.targetOrderId
			?: throw ValidationException(listOf(FieldError("targetOrderId", ErrorCode.FIELD_REQUIRED)))
		if (targetId == orderId) throw ApiException(ErrorCode.ORDER_STATUS_INVALID)
		// Both orders are locked, lowest id first, so two transfers between the same pair never wait on each other in a
		// circle and the target's picked quantity is never lost.
		val locked = listOf(orderId, targetId).sortedBy { it.toString() }.map { orders.lockById(it) ?: throw ApiException(ErrorCode.NOT_FOUND) }
		val source = locked.first { it.id == orderId }
		val target = locked.first { it.id == targetId }
		user.requireWrite(REASSIGN, source.warehouseId)
		requireStatus(source, Status.CREATED, Status.FULFILLING)
		user.requireRead(VIEW, target.warehouseId)
		if (target.warehouseId != source.warehouseId) throw ApiException(ErrorCode.ORDER_WAREHOUSE_MISMATCH)
		requireStatus(target, Status.CREATED, Status.FULFILLING)
		val note = checkNote(request.note)
		val check = Validator()
		val line = lineOf(check, source, request.lineId)
		quantity(check, "quantity", request.quantity, line?.let { products.stockProducts(listOf(it.productId))[it.productId] })
		check.check()
		val from = line!!
		val targetLine = lines.findByOrderId(target.id!!).firstOrNull { it.productId == from.productId }
			?: throw ApiException(ErrorCode.PRODUCT_NOT_IN_ORDER)
		OrderRules.reassignableQuantity(from.pickedQuantity, targetLine.reserved, request.quantity!!)?.let { throw ApiException(it) }
		val taken = takePicks(from, request.quantity)
		taken.forEach { picks.save(OrderPick(targetLine.id!!, it.locationId, it.quantity, it.movementId, it.pickedBy, it.pickedAt)) }
		from.pickedQuantity = from.pickedQuantity.subtract(request.quantity)
		targetLine.pickedQuantity = targetLine.pickedQuantity.add(request.quantity)
		touch(source)
		touch(target)
		audit.record(
			user.actor, "ORDER_PICKS_TRANSFERRED", "order", source.id.toString(),
			after = mapOf(
				"lineId" to from.id.toString(), "targetOrderId" to target.id.toString(), "targetLineId" to targetLine.id.toString(),
				"quantity" to request.quantity, "note" to note,
			),
		)
		// The target may now have every line picked: it is FULFILLED, as if its operator had picked it.
		advance(target, user.actor)
		return views.full(source, language)
	}

	// Picked quantities go back to a location of the warehouse, chosen by the operator: the only reassignment that
	// changes a balance, through an IN_RETURN movement.
	@Transactional
	fun returnPicks(user: CurrentUser, orderId: UUID, request: ReturnPicksRequest, language: String): OrderView {
		val order = openForReassignment(user, orderId)
		val note = checkNote(request.note)
		val check = Validator()
		val line = lineOf(check, order, request.lineId)
		val target = request.locationId?.let { locations.stockTargets(listOf(it))[it] }
		when {
			request.locationId == null -> check.reject("locationId", ErrorCode.FIELD_REQUIRED)
			target == null -> check.reject("locationId", ErrorCode.CATALOG_VALUE_INVALID)
			target.warehouseId != order.warehouseId -> check.reject("locationId", ErrorCode.LOCATION_NOT_IN_WAREHOUSE)
		}
		quantity(check, "quantity", request.quantity, line?.let { products.stockProducts(listOf(it.productId))[it.productId] })
		check.check()
		val from = line!!
		OrderRules.reassignableQuantity(from.pickedQuantity, null, request.quantity!!)?.let { throw ApiException(it) }
		val movement = stock.apply(
			user.actor,
			listOf(MovementCommand(StockService.IN_RETURN, target!!.id, from.productId, request.quantity, "Orden ${order.number}", SOURCE, order.id.toString())),
		).single()
		takePicks(from, request.quantity)
		from.pickedQuantity = from.pickedQuantity.subtract(request.quantity)
		touch(order)
		audit.record(
			user.actor, "ORDER_PICKS_RETURNED", "order", order.id.toString(),
			after = mapOf("lineId" to from.id.toString(), "locationId" to target.id.toString(), "quantity" to request.quantity, "movementId" to movement.id.toString(), "note" to note),
		)
		return views.full(order, language)
	}

	// An order that can be reassigned: it exists, the reader may change it, and it is still open (section 8).
	private fun openForReassignment(user: CurrentUser, orderId: UUID): Order {
		val order = orders.lockById(orderId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		user.requireWrite(REASSIGN, order.warehouseId)
		requireStatus(order, Status.CREATED, Status.FULFILLING)
		return order
	}

	// One line of the order, or the field error that says which part of the request is missing.
	private fun lineOf(check: Validator, order: Order, lineId: UUID?): OrderLine? {
		val line = lineId?.let { id -> lines.findById(id).orElse(null)?.takeIf { it.orderId == order.id } }
		when {
			lineId == null -> check.reject("lineId", ErrorCode.FIELD_REQUIRED)
			line == null -> check.reject("lineId", ErrorCode.CATALOG_VALUE_INVALID)
		}
		return line
	}

	// Takes `quantity` off the line's picks, oldest first: each pick falls, and one that falls to zero goes. The picks
	// it took are answered, with the movement they came from, so a move to another order can hand them to its line.
	private fun takePicks(line: OrderLine, quantity: BigDecimal): List<OrderPick> {
		val taken = mutableListOf<OrderPick>()
		var left = quantity
		for (pick in picks.findByLineIdIn(listOf(line.id!!)).sortedBy { it.pickedAt }) {
			if (left.signum() <= 0) break
			val moved = minOf(pick.quantity, left)
			taken += OrderPick(line.id!!, pick.locationId, moved, pick.movementId, pick.pickedBy, pick.pickedAt)
			if (moved.compareTo(pick.quantity) == 0) {
				picks.delete(pick)
			} else {
				pick.quantity = pick.quantity.subtract(moved)
				picks.save(pick)
			}
			left = left.subtract(moved)
		}
		// The line's picked quantity is the sum of its picks, so this cannot happen; it guards a broken invariant.
		if (left.signum() > 0) throw ApiException(ErrorCode.REASSIGN_EXCEEDS_PICKED)
		return taken
	}

	// For other modules: an order the person may read, by id.
	fun readable(user: CurrentUser, id: UUID): Order {
		val order = orders.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		user.requireRead(VIEW, order.warehouseId)
		return order
	}

	fun status(order: Order): Status = Status.valueOf(catalogs.itemViews(listOf(order.statusId), "es").getValue(order.statusId).code)

	fun statusId(status: Status): UUID = catalogs.itemId(STATUS_CATALOG, status.name) ?: error("ORDER_STATUS has no ${status.name}")

	// Moves the order to the next status, and only to it (order-fulfillment.md section 6).
	fun move(actor: Actor, order: Order, to: Status) {
		val from = status(order)
		if (!OrderRules.canMove(from, to)) throw ApiException(ErrorCode.ORDER_STATUS_INVALID)
		order.statusId = statusId(to)
		touch(order)
		audit.record(actor, "ORDER_STATUS_CHANGED", "order", order.id.toString(), mapOf("status" to from.name), mapOf("status" to to.name))
	}

	fun requireStatus(order: Order, vararg allowed: Status) {
		val current = status(order)
		if (current == Status.VALIDATED) throw ApiException(ErrorCode.ORDER_LOCKED)
		if (current !in allowed) throw ApiException(ErrorCode.ORDER_STATUS_INVALID)
	}

	private fun touch(order: Order) {
		order.updatedAt = clock.instant()
	}

	// The order follows its lines: the first one picked makes it FULFILLING, and every line picked makes it FULFILLED.
	// A single pick or transfer can complete it straight from CREATED, passing through FULFILLING on the way.
	private fun advance(order: Order, actor: Actor) {
		val all = lines.findByOrderId(order.id!!)
		if (all.isNotEmpty() && all.all { it.picked }) {
			if (status(order) == Status.CREATED) move(actor, order, Status.FULFILLING)
			move(actor, order, Status.FULFILLED)
		} else if (status(order) == Status.CREATED) {
			move(actor, order, Status.FULFILLING)
		}
	}

	// The assignment of an order goes to one active operator of its warehouse (order-fulfillment.md section 7a).
	private fun operator(check: Validator, warehouseId: UUID, operatorId: UUID?) {
		val operators = staff.orderOperators(warehouseId)
		when {
			operatorId == null -> check.reject("operatorId", ErrorCode.FIELD_REQUIRED)
			operatorId !in operators -> check.reject("operatorId", ErrorCode.CATALOG_VALUE_INVALID)
		}
	}

	// The note a judgement needs: present, and at most 500 characters.
	private fun checkNote(value: String?): String {
		val note = value?.trim()?.ifEmpty { null }
			?: throw ValidationException(listOf(FieldError("note", ErrorCode.FIELD_REQUIRED)))
		if (note.length > 500) throw ValidationException(listOf(FieldError("note", ErrorCode.FIELD_TOO_LONG)))
		return note
	}

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
		const val SOURCE = "order"
		const val BUSINESS_ZONE = "America/Mexico_City"
	}
}
