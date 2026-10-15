package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.db.TableRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// The reports the owner reads (docs/modules/reports.md). They read; they never change stock. Every report answers the
// table contract of api.md section 2 with the totals the screen groups by, and the same rows feed its export.

// A reference the reports show: a warehouse, a location, a material or a person.
data class ReportRef(val id: UUID, val code: String?, val name: String?)

data class ReportUnit(val code: String?, val label: String?, val decimalPlaces: Int?)

// One total of the report: a group, and what its rows add up to.
data class ReportTotal(val key: String, val label: String, val quantity: BigDecimal, val rows: Long)

// One balance as the current stock report lists it (reports.md section 3).
data class StockReportRow(
	val warehouse: ReportRef,
	val aisle: String?,
	val rack: String?,
	val location: ReportRef,
	val product: ReportRef,
	val unit: ReportUnit?,
	val quantity: BigDecimal,
	val minimum: BigDecimal,
	// Set when the material is below its minimum, over the warehouses the reader reaches.
	val shortfall: BigDecimal? = null,
) {
	// The group by aisle and rack, as the screen shows it: "K05".
	val group: String get() = listOfNotNull(aisle, rack).joinToString("").ifEmpty { "-" }
}

data class StockReportTotals(val quantity: BigDecimal, val warehouses: List<ReportTotal>, val groups: List<ReportTotal>)

// The answer of the current stock report: the page of rows, and the totals of every row that matched, not only of the
// page. `total` and the rest of the page fields are the table contract's.
data class StockReportPage(
	val data: List<StockReportRow>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
	val quantity: BigDecimal,
	val warehouseTotals: List<ReportTotal>,
	val groupTotals: List<ReportTotal>,
)

// One movement as the kardex and the movements report list it (reports.md section 3).
data class MovementReportRow(
	val seq: Long,
	val occurredAt: Instant,
	val type: String?,
	val warehouse: ReportRef,
	val location: ReportRef,
	val product: ReportRef,
	val unit: ReportUnit?,
	val quantity: BigDecimal,
	val incoming: Boolean,
	val runningLocation: BigDecimal? = null,
	val runningWarehouse: BigDecimal? = null,
	val user: ReportRef? = null,
	val reason: String? = null,
	val sourceType: String? = null,
	val sourceId: String? = null,
)

data class KardexProduct(val product: ReportRef, val warehouse: ReportRef, val unit: ReportUnit?, val closing: BigDecimal)

data class KardexPage(
	val product: KardexProduct?,
	val data: List<MovementReportRow>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
)

data class MovementsPage(
	val data: List<MovementReportRow>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
	val incoming: BigDecimal,
	val outgoing: BigDecimal,
	val typeTotals: List<ReportTotal>,
)

// One line of an order as the orders report lists it (reports.md section 3).
data class OrderReportRow(
	val order: ReportRef,
	val warehouse: ReportRef,
	val customer: ReportRef,
	val status: String?,
	val dueDate: LocalDate,
	val product: ReportRef,
	val asked: BigDecimal,
	val picked: BigDecimal,
	val movedOut: BigDecimal,
	val movedIn: BigDecimal,
	val createdAt: Instant,
	val validatedAt: Instant?,
)

data class OrderReportPage(
	val data: List<OrderReportRow>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
	val asked: BigDecimal,
	val picked: BigDecimal,
	val movedOut: BigDecimal,
	val movedIn: BigDecimal,
	val statusTotals: List<ReportTotal>,
)

// One invoice line as the entries report lists it (reports.md section 3).
data class InvoiceReportRow(
	val invoice: ReportRef,
	val supplier: ReportRef,
	val warehouse: ReportRef,
	val status: String?,
	val invoiceDate: LocalDate,
	val product: ReportRef,
	val expected: BigDecimal,
	val counted: BigDecimal,
	val difference: BigDecimal,
	val placed: BigDecimal,
)

data class InvoiceReportPage(
	val data: List<InvoiceReportRow>,
	val total: Long,
	val page: Int,
	val pageIndex: Int,
	val totalPages: Int,
	val totalCount: Long,
	val limit: Int,
	val hasPreviousPage: Boolean,
	val hasNextPage: Boolean,
	val expected: BigDecimal,
	val counted: BigDecimal,
	val difference: BigDecimal,
	val placed: BigDecimal,
	val statusTotals: List<ReportTotal>,
)

// The reports a file can be asked for, and the two shapes it comes in (reports.md section 4).
enum class ReportKind { STOCK, KARDEX, MOVEMENTS, ORDERS, INVOICES }

enum class ReportFormat { XLSX, PDF }

data class ExportRequest(val format: String? = null, val filters: Map<String, Any?>? = null, val limit: Int? = null, val sort: Any? = null)

// The rows of any report as the export needs them: the headings, and one list of cells per row.
data class ReportDocument(val title: String, val columns: List<String>, val rows: List<List<String>>, val totals: List<String> = emptyList())

// What the request of a report carries: the table contract, and the filters the report knows.
typealias ReportRequest = TableRequest
