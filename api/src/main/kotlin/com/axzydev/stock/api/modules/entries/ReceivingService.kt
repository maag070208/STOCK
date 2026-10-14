package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.entries.InvoiceRules.Status
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockRules
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.modules.warehouses.LocationService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CountRequest(val countedQuantity: BigDecimal? = null, val notes: String? = null)

data class AdjustmentRequest(val note: String? = null)

data class PlacementBody(val locationId: UUID? = null, val quantity: BigDecimal? = null)

data class PlacementsRequest(val placements: List<PlacementBody>? = null)

// An invoice with the lines and assignments of one operator, for their list in the app.
data class MyReceiptView(val invoice: InvoiceView, val lines: List<LineView>)

data class DiscrepancyView(
	val assignmentId: UUID,
	val invoice: Ref,
	val warehouse: Ref,
	val product: Ref,
	val operator: Ref,
	val expectedQuantity: BigDecimal,
	val countedQuantity: BigDecimal,
	val difference: BigDecimal,
	val countNotes: String?,
	val countedAt: Instant?,
)

// Material receiving (material-receiving.md): the operator counts each assignment, a discrepancy is adjusted to the
// counted quantity, and the counted material is placed into locations of the invoice's warehouse, which creates stock.
// The invoice moves OPEN, PENDING, IN_ASSIGNMENT, CLOSED as these complete. Every operation on an invoice locks its row
// first, so two operators working on the same invoice at the same moment always see the stored state.
@Service
class ReceivingService(
	private val invoices: InvoiceRepository,
	private val lines: InvoiceLineRepository,
	private val assignments: ReceiptAssignmentRepository,
	private val placements: ReceiptPlacementRepository,
	private val invoiceService: InvoiceService,
	private val views: InvoiceViews,
	private val products: ProductService,
	private val locations: LocationService,
	private val warehouses: WarehouseService,
	private val stock: StockService,
	private val audit: AuditService,
	private val clock: Clock,
) {
	// The operator's assignments that are still to count or to place, grouped by invoice.
	@Transactional(readOnly = true)
	fun mine(user: CurrentUser, language: String): List<MyReceiptView> {
		val mine = assignments.findByOperatorId(user.id).groupBy { it.lineId }
		val byInvoice = lines.findAllById(mine.keys).groupBy { it.invoiceId }
		return invoices.findAllById(byInvoice.keys)
			.filter { invoiceService.status(it) in setOf(Status.PENDING, Status.IN_ASSIGNMENT) }
			.sortedBy { it.createdAt }
			.map { invoice ->
				val full = views.full(invoice, language)
				val own = full.lines.orEmpty().map { line -> line.copy(assignments = line.assignments.filter { it.operator.id == user.id }) }.filter { it.assignments.isNotEmpty() }
				MyReceiptView(full.copy(lines = null), own)
			}
	}

	// A count, or its correction, until the invoice reaches IN_ASSIGNMENT (material-receiving.md, question 1).
	@Transactional
	fun count(user: CurrentUser, assignmentId: UUID, request: CountRequest): AssignmentView {
		val (assignment, line, invoice) = locate(assignmentId)
		user.requireRead(RECEIVE, invoice.warehouseId, setOf(assignment.operatorId))
		invoiceService.requireStatus(invoice, Status.PENDING)
		val check = Validator()
		val decimals = products.stockProducts(listOf(line.productId))[line.productId]?.decimals ?: 0
		when (val counted = request.countedQuantity) {
			null -> check.reject("countedQuantity", ErrorCode.FIELD_REQUIRED)
			else -> InvoiceRules.counted(counted, decimals)?.let { check.reject("countedQuantity", it) }
		}
		val notes = check.optional("notes", request.notes, Rules.text(500))
		check.check()
		val before = assignment.countedQuantity
		assignment.countedQuantity = request.countedQuantity
		assignment.countNotes = notes
		assignment.countedAt = clock.instant()
		assignment.updatedAt = clock.instant()
		audit.record(
			user.actor, "RECEIPT_COUNTED", "receipt_assignment", assignmentId.toString(),
			mapOf("countedQuantity" to before), mapOf("countedQuantity" to assignment.countedQuantity, "expectedQuantity" to assignment.expectedQuantity, "notes" to notes),
		)
		advance(user.actor, invoice)
		return views.assignment(assignment)
	}

	// The adjustment (material-receiving.md section 5): the expected quantity becomes the counted one, and the line
	// moves by the same difference. A note is required, and the audit keeps both values.
	@Transactional
	fun adjust(user: CurrentUser, assignmentId: UUID, request: AdjustmentRequest): AssignmentView {
		val (assignment, line, invoice) = locate(assignmentId)
		user.requireWrite(RESOLVE, invoice.warehouseId)
		invoiceService.requireStatus(invoice, Status.PENDING)
		val check = Validator()
		val note = check.required("note", request.note, Rules.text(500))
		check.check()
		val counted = assignment.countedQuantity
		if (counted == null || assignment.settled) throw ApiException(ErrorCode.INVOICE_STATUS_INVALID)
		val before = mapOf("expectedQuantity" to assignment.expectedQuantity, "lineQuantity" to line.quantity)
		val difference = InvoiceRules.difference(assignment.expectedQuantity, counted)
		val (expected, lineQuantity) = InvoiceRules.adjust(line.quantity, assignment.expectedQuantity, counted)
		assignment.expectedQuantity = expected
		assignment.updatedAt = clock.instant()
		line.quantity = lineQuantity
		audit.record(
			user.actor, "DISCREPANCY_ADJUSTED", "receipt_assignment", assignmentId.toString(), before,
			mapOf("expectedQuantity" to expected, "lineQuantity" to lineQuantity, "difference" to difference, "note" to note),
		)
		advance(user.actor, invoice)
		return views.assignment(assignment)
	}

	// Placement (material-receiving.md section 7): counted material into locations of the invoice's warehouse, split as the
	// operator wants, never more than was counted. Each placement is a RECEIPT movement.
	@Transactional
	fun place(user: CurrentUser, assignmentId: UUID, request: PlacementsRequest): AssignmentView {
		val (assignment, line, invoice) = locate(assignmentId)
		user.requireRead(PLACE, invoice.warehouseId, setOf(assignment.operatorId))
		invoiceService.requireStatus(invoice, Status.IN_ASSIGNMENT)
		val check = Validator()
		val bodies = request.placements.orEmpty()
		if (bodies.isEmpty()) check.reject("placements", ErrorCode.FIELD_REQUIRED)
		val decimals = products.stockProducts(listOf(line.productId))[line.productId]?.decimals ?: 0
		val targets = locations.stockTargets(bodies.mapNotNull { it.locationId })
		bodies.forEachIndexed { i, body ->
			when {
				body.locationId == null -> check.reject("placements.$i.locationId", ErrorCode.FIELD_REQUIRED)
				body.locationId !in targets -> check.reject("placements.$i.locationId", ErrorCode.CATALOG_VALUE_INVALID)
			}
			when (val quantity = body.quantity) {
				null -> check.reject("placements.$i.quantity", ErrorCode.FIELD_REQUIRED)
				else -> StockRules.quantity(quantity, decimals)?.let { check.reject("placements.$i.quantity", it) }
			}
		}
		check.check()
		if (bodies.any { targets.getValue(it.locationId!!).warehouseId != invoice.warehouseId }) throw ApiException(ErrorCode.LOCATION_NOT_IN_WAREHOUSE)
		if (!InvoiceRules.placementFits(assignment.countedQuantity!!, assignment.placedQuantity, bodies.map { it.quantity!! })) {
			throw ApiException(ErrorCode.PLACEMENT_EXCEEDS_COUNT)
		}
		val movements = stock.apply(
			user.actor,
			bodies.map { MovementCommand(StockService.RECEIPT, it.locationId!!, line.productId, it.quantity!!, "Factura ${invoice.number}", SOURCE, assignmentId.toString()) },
		)
		val now = clock.instant()
		placements.saveAll(bodies.zip(movements) { body, movement -> ReceiptPlacement(assignmentId, body.locationId!!, body.quantity!!, movement.id, now, user.id) })
		assignment.placedQuantity = bodies.fold(assignment.placedQuantity) { sum, body -> sum + body.quantity!! }
		assignment.updatedAt = now
		audit.record(
			user.actor, "RECEIPT_PLACED", "receipt_assignment", assignmentId.toString(),
			after = mapOf("placements" to bodies.map { "${targets.getValue(it.locationId!!).code}:${it.quantity}" }, "placedQuantity" to assignment.placedQuantity),
		)
		advance(user.actor, invoice)
		return views.assignment(assignment)
	}

	// The open discrepancies the reader can resolve, in their warehouses.
	@Transactional(readOnly = true)
	fun discrepancies(user: CurrentUser, request: TableRequest, language: String): TablePage<DiscrepancyView> {
		val wanted = (request.filters?.get("warehouseId") as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
		val allowed = if (user.scopeOf(RESOLVE) == Scope.ALL) null else user.warehouseIds
		val warehouseIds = listOfNotNull(wanted).ifEmpty { allowed?.toList() ?: warehouses.allIds() }.filter { allowed == null || it in allowed }
		val open = if (warehouseIds.isEmpty()) emptyList() else assignments.openDiscrepancies(warehouseIds)
		val lineById = lines.findAllById(open.map { it.lineId }).associateBy { it.id!! }
		val invoiceById = invoices.findAllById(lineById.values.map { it.invoiceId }).associateBy { it.id!! }
		val headers = views.headers(invoiceById.values.toList(), language).associateBy { it.id }
		val found = products.stockProducts(lineById.values.map { it.productId })
		val shares = open.associateWith { views.assignment(it) }
		val rows = open.map { assignment ->
			val line = lineById.getValue(assignment.lineId)
			val header = headers.getValue(line.invoiceId)
			DiscrepancyView(
				assignmentId = assignment.id!!,
				invoice = Ref(header.id, header.number, header.supplier.name),
				warehouse = header.warehouse,
				product = found[line.productId].let { Ref(line.productId, it?.code, it?.name) },
				operator = shares.getValue(assignment).operator,
				expectedQuantity = assignment.expectedQuantity,
				countedQuantity = assignment.countedQuantity!!,
				difference = InvoiceRules.difference(assignment.expectedQuantity, assignment.countedQuantity!!),
				countNotes = assignment.countNotes,
				countedAt = assignment.countedAt,
			)
		}.sortedBy { it.countedAt }
		val limit = (request.limit ?: TableSpec.DEFAULT_LIMIT).coerceIn(1, TableSpec.MAX_LIMIT)
		val page = (request.page ?: 1).coerceAtLeast(1)
		val pages = if (rows.isEmpty()) 0 else (rows.size + limit - 1) / limit
		return TablePage(rows.drop((page - 1) * limit).take(limit), rows.size.toLong(), page, page - 1, pages, rows.size.toLong(), limit, page > 1, page < pages)
	}

	// The assignment with its line and its invoice. The invoice row is locked until the transaction ends.
	private fun locate(assignmentId: UUID): Triple<ReceiptAssignment, InvoiceLine, Invoice> {
		val invoiceId = assignments.invoiceIdOf(assignmentId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		val invoice = invoices.lockById(invoiceId) ?: throw ApiException(ErrorCode.NOT_FOUND)
		// Loaded only after the lock, so this sees what the operation before it left.
		val assignment = assignments.findById(assignmentId).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		val line = lines.findById(assignment.lineId).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		return Triple(assignment, line, invoice)
	}

	// Moves the invoice as far as its assignments allow: IN_ASSIGNMENT when every line is assigned and every share is
	// counted and settled; CLOSED when every share is placed (material-receiving.md sections 6 to 8).
	private fun advance(actor: Actor, invoice: Invoice) {
		assignments.flush()
		val all = lines.findByInvoiceId(invoice.id!!)
		val shares = assignments.findByLineIdIn(all.map { it.id!! })
		val assigned = all.all { line -> line.quantity.signum() == 0 || shares.any { it.lineId == line.id } }
		if (invoiceService.status(invoice) == Status.PENDING && assigned && shares.isNotEmpty() && shares.all { it.settled }) {
			invoiceService.move(actor, invoice, Status.IN_ASSIGNMENT)
		}
		if (invoiceService.status(invoice) == Status.IN_ASSIGNMENT && shares.all { it.placed }) {
			invoiceService.move(actor, invoice, Status.CLOSED)
		}
	}

	companion object {
		const val RECEIVE = "receipts.receive"
		const val PLACE = "receipts.place"
		const val RESOLVE = "discrepancies.resolve"
		const val VIEW = "receipts.view"
		const val SOURCE = "receipt_assignment"
	}
}
