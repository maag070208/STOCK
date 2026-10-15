package com.axzydev.stock.api.modules.entries

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
import com.axzydev.stock.api.modules.entries.InvoiceRules.Status
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.StockProduct
import com.axzydev.stock.api.modules.stock.StockRules
import com.axzydev.stock.api.modules.suppliers.SupplierService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class AssignmentBody(val operatorId: UUID? = null, val quantity: BigDecimal? = null)

data class LineBody(val productId: UUID? = null, val quantity: BigDecimal? = null, val assignments: List<AssignmentBody>? = null)

// Path A sends no assignments and assigns later; path B sends them with the lines and saves once (material-entry.md section 5).
data class CreateInvoiceRequest(
	val number: String? = null,
	val supplierId: UUID? = null,

	val invoiceDate: LocalDate? = null,
	val warehouseId: UUID? = null,
	val lines: List<LineBody>? = null,
)

data class AddLineRequest(val productId: UUID? = null, val quantity: BigDecimal? = null)

// The unit is not here: it comes from the material and does not change.
data class UpdateLineRequest(val quantity: BigDecimal? = null)

data class AssignmentsRequest(val assignments: List<AssignmentBody>? = null)

// Material entry (material-entry.md): the invoice, its lines, and the assignment of each line to operators.
// Counting, adjusting and placing are in ReceivingService.
@Service
class InvoiceService(
	private val invoices: InvoiceRepository,
	private val lines: InvoiceLineRepository,
	private val assignments: ReceiptAssignmentRepository,
	private val staff: StaffTemplateService,
	private val suppliers: SupplierService,
	private val warehouses: WarehouseService,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val views: InvoiceViews,
	private val audit: AuditService,
	private val clock: Clock,
) {
	// One status code, or several: the list asks for the open ones (OPEN, PENDING and IN_ASSIGNMENT) in one query.
	private val statusFilter: TableFilter<Invoice> = { root, builder, value ->
		val codes = (value as? List<*>)?.map { it.toString() } ?: listOf(value.toString())
		val ids = codes.mapNotNull { catalogs.itemId(STATUS_CATALOG, it) }
		if (ids.isEmpty() || ids.size != codes.size) builder.disjunction() else root.get<UUID>("statusId").`in`(ids)
	}

	// One box for the list (F8-02): the invoice number, the supplier's code or its name, each with OR among themselves
	// and with AND against every other filter.
	private val searchFilter: TableFilter<Invoice> = { root, builder, value ->
		val term = "%" + value.toString().trim().lowercase() + "%"
		val supplierIds = suppliers.idsMatching(value.toString().trim())
		builder.or(
			builder.like(builder.lower(root.get("number")), term),
			if (supplierIds.isEmpty()) builder.disjunction() else root.get<UUID>("supplierId").`in`(supplierIds),
		)
	}

	private val table = TableSpec<Invoice>(
		filters = mapOf(
			"number" to TableSpec.contains("number"),
			"search" to searchFilter,
			"supplierId" to TableSpec.id("supplierId"),
			"warehouseId" to TableSpec.id("warehouseId"),
			"status" to statusFilter,
		),
		sorts = mapOf("number" to "number", "invoiceDate" to "invoiceDate", "createdAt" to "createdAt"),
		defaultSort = Sort.by(Sort.Direction.DESC, "createdAt"),
	)

	// With AREA, the invoices of the reader's warehouses only.
	@Transactional(readOnly = true)
	fun query(user: CurrentUser, request: TableRequest, language: String): TablePage<InvoiceView> {
		val scope = if (user.scopeOf(VIEW) == Scope.ALL) null else Specification<Invoice> { root, _, builder ->
			if (user.warehouseIds.isEmpty()) builder.disjunction() else root.get<UUID>("warehouseId").`in`(user.warehouseIds)
		}
		return invoices.table(request, table, scope) { it }.mapRows { rows -> views.headers(rows, language) }
	}

	@Transactional(readOnly = true)
	fun get(user: CurrentUser, id: UUID, language: String): InvoiceView = views.full(readable(user, id), language)

	// Closing by hand (F8-03): for the invoice captured fast, which never had operators. Only while nothing has been
	// counted, so the work of the receiving is never thrown away; from there on it closes itself.
	@Transactional
	fun close(user: CurrentUser, id: UUID, language: String): InvoiceView {
		val invoice = readable(user, id)
		val current = status(invoice)
		val lines = lines.countByInvoiceId(invoice.id!!)
		val counted = assignments.countCountedOfInvoice(invoice.id!!)
		InvoiceRules.canClose(current, lines, counted)?.let { throw ApiException(it) }
		invoice.statusId = statusId(Status.CLOSED)
		touch(invoice)
		audit.record(user.actor, "INVOICE_CLOSED", "invoice", id.toString(), before = mapOf("status" to current.name), after = mapOf("status" to Status.CLOSED.name, "lines" to lines))
		return views.full(invoice, language)
	}

	@Transactional(readOnly = true)
	fun warehouseOptions(): List<Ref> = warehouses.activeOptions().map { Ref(it.first, it.second, it.third) }

	@Transactional
	fun create(user: CurrentUser, request: CreateInvoiceRequest, language: String): InvoiceView {
		val check = Validator()
		val number = check.required("number", request.number, InvoiceRules::number) { it.uppercase() }
		suppliers.requireForNewRecord(check, request.supplierId, null)
		// The date is required and is a date; a future one is allowed (F8-01): the office records the supplier's paper.
		if (request.invoiceDate == null) check.reject("invoiceDate", ErrorCode.FIELD_REQUIRED)
		warehouses.requireActive(check, "warehouseId", request.warehouseId)
		val bodies = request.lines.orEmpty()
		val found = products.stockProducts(bodies.mapNotNull { it.productId })
		val operators = request.warehouseId?.let { staff.operators(it).keys }.orEmpty()
		val seen = mutableSetOf<UUID>()
		bodies.forEachIndexed { i, line ->
			val product = line.productId?.let(found::get)
			when {
				line.productId == null -> check.reject("lines.$i.productId", ErrorCode.FIELD_REQUIRED)
				product == null || !product.active -> check.reject("lines.$i.productId", ErrorCode.CATALOG_VALUE_INVALID)
				!seen.add(line.productId) -> check.reject("lines.$i.productId", ErrorCode.DUPLICATE_VALUE)
			}
			quantity(check, "lines.$i.quantity", line.quantity, product)
			line.assignments?.let { shares(check, "lines.$i.assignments", it, line.quantity, product, operators) }
		}
		check.check()
		if (invoices.existsBySupplierIdAndNumber(request.supplierId!!, number)) throw ValidationException(listOf(FieldError("number", ErrorCode.DUPLICATE_VALUE)))

		val now = clock.instant()
		val invoice = invoices.save(Invoice(number, request.supplierId, null, request.invoiceDate!!, request.warehouseId!!, statusId(Status.OPEN), user.id, now, now))
		for (body in bodies) {
			val line = lines.save(InvoiceLine(invoice.id!!, body.productId!!, found.getValue(body.productId).unitId, body.quantity!!))
			body.assignments?.forEach { assignments.save(ReceiptAssignment(line.id!!, it.operatorId!!, it.quantity!!, now, now)) }
		}
		audit.record(user.actor, "INVOICE_CREATED", "invoice", invoice.id.toString(), after = mapOf("number" to number, "lines" to bodies.size))
		if (bodies.any { !it.assignments.isNullOrEmpty() }) move(user.actor, invoice, Status.PENDING)
		return views.full(invoice, language)
	}

	@Transactional
	fun addLine(user: CurrentUser, invoiceId: UUID, request: AddLineRequest, language: String): LineView {
		val invoice = editable(user, invoiceId)
		val check = Validator()
		val product = request.productId?.let { products.stockProducts(listOf(it))[it] }
		when {
			request.productId == null -> check.reject("productId", ErrorCode.FIELD_REQUIRED)
			product == null || !product.active -> check.reject("productId", ErrorCode.CATALOG_VALUE_INVALID)
			lines.findByInvoiceId(invoiceId).any { it.productId == request.productId } -> check.reject("productId", ErrorCode.DUPLICATE_VALUE)
		}
		quantity(check, "quantity", request.quantity, product)
		check.check()
		val line = lines.save(InvoiceLine(invoiceId, request.productId!!, product!!.unitId, request.quantity!!))
		audit.record(user.actor, "INVOICE_LINE_ADDED", "invoice", invoiceId.toString(), after = mapOf("productId" to line.productId.toString(), "quantity" to line.quantity))
		touch(invoice)
		return views.line(line, language)
	}

	// The quantity of a line changes only while no share of it is counted, and its shares must still add up to it.
	@Transactional
	fun updateLine(user: CurrentUser, invoiceId: UUID, lineId: UUID, request: UpdateLineRequest, language: String): LineView {
		val invoice = editable(user, invoiceId)
		val line = lineOf(invoiceId, lineId)
		val shares = assignments.findByLineIdIn(listOf(lineId))
		if (shares.any { it.counted }) throw ApiException(ErrorCode.ASSIGNMENT_LOCKED)
		val check = Validator()
		quantity(check, "quantity", request.quantity, products.stockProducts(listOf(line.productId))[line.productId])
		check.check()
		if (shares.isNotEmpty() && !InvoiceRules.sharesAddUp(request.quantity!!, shares.map { it.expectedQuantity })) {
			throw ValidationException(listOf(FieldError("quantity", ErrorCode.ASSIGNMENT_TOTAL_MISMATCH)))
		}
		val before = line.quantity
		line.quantity = request.quantity!!
		audit.record(user.actor, "INVOICE_LINE_UPDATED", "invoice", invoiceId.toString(), mapOf("lineId" to lineId.toString(), "quantity" to before), mapOf("lineId" to lineId.toString(), "quantity" to line.quantity))
		touch(invoice)
		return views.line(line, language)
	}

	// A line added by mistake is removed while nothing of it is counted. The audit log keeps what it was.
	@Transactional
	fun removeLine(user: CurrentUser, invoiceId: UUID, lineId: UUID) {
		val invoice = editable(user, invoiceId)
		val line = lineOf(invoiceId, lineId)
		val shares = assignments.findByLineIdIn(listOf(lineId))
		if (shares.any { it.counted }) throw ApiException(ErrorCode.ASSIGNMENT_LOCKED)
		assignments.deleteAll(shares)
		lines.delete(line)
		audit.record(user.actor, "INVOICE_LINE_REMOVED", "invoice", invoiceId.toString(), before = mapOf("productId" to line.productId.toString(), "quantity" to line.quantity))
		touch(invoice)
	}

	// Replaces the shares of a line (path A, or a change before counting). The first share moves the invoice to PENDING.
	@Transactional
	fun setAssignments(user: CurrentUser, invoiceId: UUID, lineId: UUID, request: AssignmentsRequest, language: String): LineView {
		val invoice = readable(user, invoiceId)
		user.requireWrite(ASSIGN, invoice.warehouseId)
		requireStatus(invoice, Status.OPEN, Status.PENDING)
		val line = lineOf(invoiceId, lineId)
		val current = assignments.findByLineIdIn(listOf(lineId))
		if (current.any { it.counted }) throw ApiException(ErrorCode.ASSIGNMENT_LOCKED)
		val check = Validator()
		val bodies = request.assignments ?: emptyList<AssignmentBody>().also { check.reject("assignments", ErrorCode.FIELD_REQUIRED) }
		shares(check, "assignments", bodies, line.quantity, products.stockProducts(listOf(line.productId))[line.productId], staff.operators(invoice.warehouseId).keys)
		check.check()
		assignments.deleteAll(current)
		assignments.flush()
		val now = clock.instant()
		bodies.forEach { assignments.save(ReceiptAssignment(lineId, it.operatorId!!, it.quantity!!, now, now)) }
		audit.record(
			user.actor, "RECEIPT_ASSIGNMENTS_CHANGED", "invoice", invoiceId.toString(),
			mapOf("lineId" to lineId.toString(), "shares" to current.map { "${it.operatorId}:${it.expectedQuantity}" }),
			mapOf("lineId" to lineId.toString(), "shares" to bodies.map { "${it.operatorId}:${it.quantity}" }),
		)
		if (status(invoice) == Status.OPEN) move(user.actor, invoice, Status.PENDING) else touch(invoice)
		return views.line(line, language)
	}

	// For receiving: an invoice the person may read, by id.
	fun readable(user: CurrentUser, id: UUID): Invoice {
		val invoice = invoices.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		user.requireRead(VIEW, invoice.warehouseId)
		return invoice
	}

	fun status(invoice: Invoice): Status = Status.valueOf(catalogs.itemViews(listOf(invoice.statusId), "es").getValue(invoice.statusId).code)

	// Moves the invoice to the next status, and only to it (material-receiving.md section 8).
	fun move(actor: Actor, invoice: Invoice, to: Status) {
		val from = status(invoice)
		if (!InvoiceRules.canMove(from, to)) throw ApiException(ErrorCode.INVOICE_STATUS_INVALID)
		invoice.statusId = statusId(to)
		touch(invoice)
		audit.record(actor, "INVOICE_STATUS_CHANGED", "invoice", invoice.id.toString(), mapOf("status" to from.name), mapOf("status" to to.name))
	}

	fun requireStatus(invoice: Invoice, vararg allowed: Status) {
		val current = status(invoice)
		if (current == Status.CLOSED) throw ApiException(ErrorCode.INVOICE_LOCKED)
		if (current !in allowed) throw ApiException(ErrorCode.INVOICE_STATUS_INVALID)
	}

	private fun statusId(status: Status): UUID = catalogs.itemId(STATUS_CATALOG, status.name) ?: error("INVOICE_STATUS has no ${status.name}")

	private fun editable(user: CurrentUser, invoiceId: UUID): Invoice = readable(user, invoiceId).also { requireStatus(it, Status.OPEN, Status.PENDING) }

	private fun lineOf(invoiceId: UUID, lineId: UUID): InvoiceLine =
		lines.findById(lineId).orElse(null)?.takeIf { it.invoiceId == invoiceId } ?: throw ApiException(ErrorCode.NOT_FOUND)

	private fun touch(invoice: Invoice) {
		invoice.updatedAt = clock.instant()
	}

	private fun quantity(check: Validator, field: String, value: BigDecimal?, product: StockProduct?) {
		when {
			value == null -> check.reject(field, ErrorCode.FIELD_REQUIRED)
			else -> StockRules.quantity(value, product?.decimals ?: 0)?.let { check.reject(field, it) }
		}
	}

	// The shares of a line: each one an operator of the warehouse's template, once, with a valid quantity, and all of
	// them adding up to the line quantity.
	private fun shares(check: Validator, field: String, bodies: List<AssignmentBody>, line: BigDecimal?, product: StockProduct?, operators: Set<UUID>) {
		val seen = mutableSetOf<UUID>()
		bodies.forEachIndexed { j, share ->
			when {
				share.operatorId == null -> check.reject("$field.$j.operatorId", ErrorCode.FIELD_REQUIRED)
				share.operatorId !in operators -> check.reject("$field.$j.operatorId", ErrorCode.CATALOG_VALUE_INVALID)
				!seen.add(share.operatorId) -> check.reject("$field.$j.operatorId", ErrorCode.DUPLICATE_VALUE)
			}
			quantity(check, "$field.$j.quantity", share.quantity, product)
		}
		val quantities = bodies.mapNotNull { it.quantity }
		if (line != null && quantities.size == bodies.size && !InvoiceRules.sharesAddUp(line, quantities)) check.reject(field, ErrorCode.ASSIGNMENT_TOTAL_MISMATCH)
	}

	companion object {
		const val VIEW = "invoices.view"
		const val CREATE = "invoices.create"
		const val ASSIGN = "receipts.assign"
		const val STATUS_CATALOG = "INVOICE_STATUS"
		// The business works in Mexico City time: an invoice date is not later than today there.
		const val BUSINESS_ZONE = "America/Mexico_City"
	}
}
