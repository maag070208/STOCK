package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

// The reports (docs/modules/reports.md). They read only. Every report takes the table contract, so its screen and its
// export share the filters and the order.
@RestController
@RequestMapping("/api/v1/reports")
class ReportsController(private val reports: ReportsService, private val exports: ReportsExportService) {

	// The export of a report (F6-05): the same rows as the screen, as an XLSX or a PDF, written by the API and audited.
	@PostMapping("/{report}/export")
	@RequiresPermission(ReportsService.EXPORT)
	fun export(
		@PathVariable report: String,
		@RequestBody request: ExportRequest,
		user: CurrentUser,
		locale: Locale,
	): ResponseEntity<ByteArray> {
		val kind = ReportKind.entries.firstOrNull { it.name.equals(report, ignoreCase = true) } ?: throw ApiException(ErrorCode.NOT_FOUND)
		val format = ReportFormat.entries.firstOrNull { it.name.equals(request.format, ignoreCase = true) } ?: throw ApiException(ErrorCode.CATALOG_VALUE_INVALID)
		val file = exports.export(
			user, kind, format,
			TableRequest(page = 1, limit = TableSpec.MAX_LIMIT, filters = request.filters, sort = null),
			locale.language,
		)
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName + "\"")
			.contentType(MediaType.parseMediaType(file.contentType))
			.body(file.bytes)
	}

	// The current stock: one row per balance, with the totals by warehouse, by aisle and rack, and the grand total.
	@PostMapping("/stock")
	@RequiresPermission(ReportsService.VIEW)
	fun stock(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): StockReportPage =
		reports.stock(user, request, locale.language)

	// The orders of the reader's warehouses, by period, customer and status, with what was picked and moved.
	@PostMapping("/orders")
	@RequiresPermission(ReportsService.VIEW)
	fun orders(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): OrderReportPage =
		reports.orders(user, request, locale.language)

	// The invoices and entries of the reader's warehouses, by period, supplier and status.
	@PostMapping("/invoices")
	@RequiresPermission(ReportsService.VIEW)
	fun invoices(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): InvoiceReportPage =
		reports.invoices(user, request, locale.language)

	// The movements of the reader's warehouses, by period, type, user and material.
	@PostMapping("/movements")
	@RequiresPermission(ReportsService.VIEW)
	fun movements(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): MovementsPage =
		reports.movements(user, request, locale.language)

	// The kardex of one material in one warehouse: its movements in order, with the running balance of each.
	@PostMapping("/kardex")
	@RequiresPermission(ReportsService.VIEW)
	fun kardex(@RequestBody request: TableRequest, user: CurrentUser, locale: Locale): KardexPage =
		reports.kardex(user, request, locale.language)
}
