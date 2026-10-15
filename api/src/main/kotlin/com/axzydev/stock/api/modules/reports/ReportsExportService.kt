package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.files.PdfTableWriter
import com.axzydev.stock.api.core.files.XlsxWriter
import com.axzydev.stock.api.core.i18n.DocumentLabels
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.time.BusinessTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The file a report leaves in (reports.md section 4).
data class ReportFile(val fileName: String, val contentType: String, val bytes: ByteArray)

// The exports of the reports (F6-05). The file is built by the API from the very rows the screen shows, with the same
// filters, so a screen and its file never disagree, and every export is written to the audit log.
@Service
class ReportsExportService(
	private val reports: ReportsService,
	private val audit: AuditService,
) {
	// The rows of a report as a document: the same call the screen makes, with the same filters.
	@Transactional(readOnly = true)
	fun document(user: CurrentUser, kind: ReportKind, request: TableRequest, language: String): ReportDocument =
		when (kind) {
			ReportKind.STOCK -> stockDocument(reports.stock(user, request, language), language)
			ReportKind.KARDEX -> kardexDocument(reports.kardex(user, request, language), language)
			ReportKind.MOVEMENTS -> movementsDocument(reports.movements(user, request, language), language)
			ReportKind.ORDERS -> ordersDocument(reports.orders(user, request, language), language)
			ReportKind.INVOICES -> invoicesDocument(reports.invoices(user, request, language), language)
		}

	// The file of a report. Only whoever may export asks for it, and the export itself is audited with the report, the
	// format, the filters and how many rows left the system.
	@Transactional
	fun export(user: CurrentUser, kind: ReportKind, format: ReportFormat, request: TableRequest, language: String): ReportFile {
		if (!user.can(ReportsService.EXPORT)) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
		val document = document(user, kind, request, language)
		val bytes = when (format) {
			ReportFormat.XLSX -> XlsxWriter.write(document.title, document.columns, document.rows, document.totals)
			ReportFormat.PDF -> PdfTableWriter.write(document.title, document.columns, document.rows, document.totals)
		}
		audit.record(
			user.actor, "REPORT_EXPORTED", "report", kind.name,
			after = mapOf("format" to format.name, "filters" to request.filters, "rows" to document.rows.size),
		)
		val extension = if (format == ReportFormat.XLSX) "xlsx" else "pdf"
		val contentType = if (format == ReportFormat.XLSX) {
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
		} else {
			"application/pdf"
		}
		return ReportFile("${kind.name.lowercase()}-${LocalDate.now(zone())}.$extension", contentType, bytes)
	}

	private fun stockDocument(page: StockReportPage, language: String): ReportDocument {
		val label = labels(language)
		return ReportDocument(
			title = label("report.stock_title"),
			columns = listOf(label("report.warehouse"), label("report.aisle"), label("report.rack"), label("report.location"), label("report.product_code"), label("report.product"), label("report.unit"), label("report.quantity")),
			rows = page.data.map { row ->
				listOf(row.warehouse.code.orEmpty(), row.aisle.orEmpty(), row.rack.orEmpty(), row.location.code.orEmpty(), row.product.code.orEmpty(), row.product.name.orEmpty(), row.unit?.label ?: row.unit?.code.orEmpty(), quantity(row.quantity))
			},
			totals = listOf(label("report.total"), "", "", "", "", "", "", quantity(page.quantity)),
		)
	}

	private fun kardexDocument(page: KardexPage, language: String): ReportDocument {
		val label = labels(language)
		return ReportDocument(
			title = "${label("report.kardex_title")} · ${page.product?.product?.code.orEmpty()} ${page.product?.warehouse?.code.orEmpty()}".trim(),
			columns = listOf(label("report.date"), label("report.type"), label("report.warehouse"), label("report.location"), label("report.incoming"), label("report.outgoing"), label("report.balance_location"), label("report.balance_total"), label("report.user"), label("report.reason"), label("report.source")),
			rows = page.data.map { row -> kardexCells(row, language) },
			totals = listOf(label("report.balance_total"), "", "", "", "", "", "", quantity(page.product?.closing ?: BigDecimal.ZERO)),
		)
	}

	private fun movementsDocument(page: MovementsPage, language: String): ReportDocument {
		val label = labels(language)
		return ReportDocument(
			title = label("report.movements_title"),
			columns = listOf(label("report.date"), label("report.type"), label("report.warehouse"), label("report.location"), label("report.product"), label("report.incoming"), label("report.outgoing"), label("report.user"), label("report.reason"), label("report.source")),
			rows = page.data.map { row -> movementCells(row, language) },
			totals = listOf(label("report.total"), "", "", "", "", quantity(page.incoming), quantity(page.outgoing)),
		)
	}

	// The columns both movement reports share, and the two running balances the kardex adds.
	private fun movementCells(row: MovementReportRow, language: String): List<String> = listOf(
		stamp(row.occurredAt, language),
		row.typeLabel ?: row.type.orEmpty(),
		row.warehouse.code.orEmpty(),
		row.location.code.orEmpty(),
		if (row.incoming) quantity(row.quantity) else "",
		if (row.incoming) "" else quantity(row.quantity),
		row.user?.name ?: row.user?.code.orEmpty(),
		row.reason.orEmpty(),
		row.sourceType.orEmpty(),
	)

	private fun kardexCells(row: MovementReportRow, language: String): List<String> =
		movementCells(row, language).let { cells ->
			cells.take(6) + listOf(quantity(row.runningLocation ?: BigDecimal.ZERO), quantity(row.runningWarehouse ?: BigDecimal.ZERO)) + cells.drop(6)
		}

	private fun ordersDocument(page: OrderReportPage, language: String): ReportDocument {
		val label = labels(language)
		return ReportDocument(
			title = label("report.orders_title"),
			columns = listOf(label("report.order"), label("report.warehouse"), label("report.customer"), label("report.status"), label("report.due_date"), label("report.product"), label("report.asked"), label("report.picked"), label("report.moved_out"), label("report.moved_in"), label("report.created_at"), label("report.validated_at")),
			rows = page.data.map { row ->
				listOf(
					row.order.code.orEmpty(), row.warehouse.code.orEmpty(), row.customer.name ?: row.customer.code.orEmpty(), row.statusLabel ?: row.status.orEmpty(),
					row.dueDate.toString(), row.product.code.orEmpty(), quantity(row.asked), quantity(row.picked), quantity(row.movedOut), quantity(row.movedIn),
					stamp(row.createdAt, language), row.validatedAt?.let { stamp(it, language) }.orEmpty(),
				)
			},
			totals = listOf(label("report.total"), "", "", "", "", "", quantity(page.asked), quantity(page.picked), quantity(page.movedOut), quantity(page.movedIn)),
		)
	}

	private fun invoicesDocument(page: InvoiceReportPage, language: String): ReportDocument {
		val label = labels(language)
		return ReportDocument(
			title = label("report.invoices_title"),
			columns = listOf(label("report.invoice"), label("report.supplier"), label("report.warehouse"), label("report.status"), label("report.date"), label("report.product"), label("report.expected"), label("report.counted"), label("report.difference"), label("report.placed")),
			rows = page.data.map { row ->
				listOf(
					row.invoice.code.orEmpty(), row.supplier.name ?: row.supplier.code.orEmpty(), row.warehouse.code.orEmpty(), row.statusLabel ?: row.status.orEmpty(),
					row.invoiceDate.toString(), row.product.code.orEmpty(), quantity(row.expected), quantity(row.counted), quantity(row.difference), quantity(row.placed),
				)
			},
			totals = listOf(label("report.total"), "", "", "", "", "", quantity(page.expected), quantity(page.counted), quantity(page.difference), quantity(page.placed)),
		)
	}

	private fun labels(language: String): (String) -> String = { key -> DocumentLabels.text(key, language) }

	// A quantity as a person reads it: without the trailing zeros the stored decimal carries.
	private fun quantity(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

	private fun stamp(at: Instant, language: String): String = FORMAT.format(at.atZone(zone()))

	private fun zone(): ZoneId = ZoneId.of(BusinessTime.ZONE)

	private companion object {
		val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
	}
}
