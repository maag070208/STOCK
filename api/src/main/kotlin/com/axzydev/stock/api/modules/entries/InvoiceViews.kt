package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.stock.CodeLabel
import com.axzydev.stock.api.modules.stock.UnitRef
import com.axzydev.stock.api.modules.suppliers.SupplierService
import com.axzydev.stock.api.modules.warehouses.LocationService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Ref(val id: UUID, val code: String?, val name: String?)

data class PlacementView(val id: UUID, val locationId: UUID, val locationCode: String?, val quantity: BigDecimal, val placedAt: Instant)

data class AssignmentView(
	val id: UUID,
	val lineId: UUID,
	val operator: Ref,
	val expectedQuantity: BigDecimal,
	val countedQuantity: BigDecimal?,
	// counted − expected, once counted.
	val difference: BigDecimal?,
	// MATCH or DISCREPANCY, once counted.
	val outcome: String?,
	val countNotes: String?,
	val countedAt: Instant?,
	val placedQuantity: BigDecimal,
	val placed: Boolean,
	val placements: List<PlacementView>,
)

data class LineView(val id: UUID, val product: Ref, val unit: UnitRef?, val quantity: BigDecimal, val assignments: List<AssignmentView>)

// `lines` comes with a single invoice, not with a list.
data class InvoiceView(
	val id: UUID,
	val number: String,
	val supplier: Ref,
	val contact: Ref?,
	val invoiceDate: LocalDate,
	val warehouse: Ref,
	val status: CodeLabel,
	val createdAt: Instant,
	val lines: List<LineView>?,
)

// Builds what the API answers about invoices. Every name, code and label of a page is looked up together.
@Component
class InvoiceViews(
	private val lines: InvoiceLineRepository,
	private val assignments: ReceiptAssignmentRepository,
	private val placements: ReceiptPlacementRepository,
	private val suppliers: SupplierService,
	private val warehouses: WarehouseService,
	private val locations: LocationService,
	private val products: ProductService,
	private val people: UserService,
	private val catalogs: CatalogService,
) {
	fun headers(invoices: List<Invoice>, language: String): List<InvoiceView> {
		val (supplierNames, contactNames) = suppliers.names(invoices.map { it.supplierId }, invoices.mapNotNull { it.contactId })
		val warehouseCodes = warehouses.codes(invoices.map { it.warehouseId })
		val statuses = catalogs.itemViews(invoices.map { it.statusId }, language)
		return invoices.map { invoice ->
			InvoiceView(
				id = invoice.id!!,
				number = invoice.number,
				supplier = Ref(invoice.supplierId, supplierNames[invoice.supplierId]?.first, supplierNames[invoice.supplierId]?.second),
				contact = invoice.contactId?.let { Ref(it, null, contactNames[it]) },
				invoiceDate = invoice.invoiceDate,
				warehouse = Ref(invoice.warehouseId, warehouseCodes[invoice.warehouseId], null),
				status = statuses[invoice.statusId].let { CodeLabel(it?.code ?: "", it?.label) },
				createdAt = invoice.createdAt,
				lines = null,
			)
		}
	}

	fun full(invoice: Invoice, language: String): InvoiceView =
		headers(listOf(invoice), language).single().copy(lines = lines(lines.findByInvoiceId(invoice.id!!), language))

	fun line(line: InvoiceLine, language: String): LineView = lines(listOf(line), language).single()

	fun assignment(assignment: ReceiptAssignment): AssignmentView = assignments(listOf(assignment)).values.single().single()

	private fun lines(list: List<InvoiceLine>, language: String): List<LineView> {
		val found = products.stockProducts(list.map { it.productId })
		val units = catalogs.itemViews(list.map { it.unitId }, language)
		val shares = assignments(assignments.findByLineIdIn(list.map { it.id!! }))
		return list.sortedBy { found[it.productId]?.code }.map { line ->
			val product = found[line.productId]
			val unit = units[line.unitId]
			LineView(
				id = line.id!!,
				product = Ref(line.productId, product?.code, product?.name),
				unit = unit?.let { UnitRef(it.code, it.label, (it.attributes["decimal_places"] as? Number)?.toInt()) },
				quantity = line.quantity,
				assignments = shares[line.id!!].orEmpty(),
			)
		}
	}

	// The assignments by line, each with its operator and its placements.
	private fun assignments(list: List<ReceiptAssignment>): Map<UUID, List<AssignmentView>> {
		val operators = people.people(list.map { it.operatorId })
		val placed = placements.findByAssignmentIdIn(list.map { it.id!! }).groupBy { it.assignmentId }
		val locationCodes = locations.stockTargets(placed.values.flatten().map { it.locationId }).mapValues { it.value.code }
		return list.sortedBy { operators[it.operatorId]?.name }.groupBy({ it.lineId }) { assignment ->
			val counted = assignment.countedQuantity
			val difference = counted?.let { InvoiceRules.difference(assignment.expectedQuantity, it) }
			AssignmentView(
				id = assignment.id!!,
				lineId = assignment.lineId,
				operator = operators[assignment.operatorId].let { Ref(assignment.operatorId, it?.username, it?.name) },
				expectedQuantity = assignment.expectedQuantity,
				countedQuantity = counted,
				difference = difference,
				outcome = difference?.let { if (it.signum() == 0) "MATCH" else "DISCREPANCY" },
				countNotes = assignment.countNotes,
				countedAt = assignment.countedAt,
				placedQuantity = assignment.placedQuantity,
				placed = assignment.placed,
				placements = placed[assignment.id].orEmpty().sortedBy { it.placedAt }.map { PlacementView(it.id!!, it.locationId, locationCodes[it.locationId], it.quantity, it.placedAt) },
			)
		}
	}
}
