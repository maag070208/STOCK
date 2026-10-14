package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.files.XlsxReader
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.modules.catalogs.CatalogService
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.Locale

data class RejectedRowView(val row: Int, val field: String, val code: String, val message: String)

// What the import found in a file. `created` is set only by the confirmation.
data class ImportReport(
	val checksum: String,
	val accepted: Int,
	val newProducts: Int,
	val existingProducts: Int,
	val rejected: List<RejectedRowView>,
	val stockTotal: BigDecimal,
	val controlTotal: Long?,
	// Null when the file carries no control total.
	val controlMatches: Boolean?,
	val created: Int? = null,
)

// The import of the legacy file (products.md section 6): a dry run that writes nothing, then a confirmation that
// writes the products in one transaction, only for the file that was reviewed. Products are matched by code, so
// running it again writes nothing new. Stock is not imported here: the opening inventory arrives with stock, in F4.
@Service
class ProductImportService(
	private val products: ProductRepository,
	private val catalogs: CatalogService,
	private val audit: AuditService,
	private val messages: MessageSource,
	private val clock: Clock,
) {
	private class Review(val report: ImportReport, val toCreate: List<LegacyProduct>)

	@Transactional(readOnly = true)
	fun dryRun(file: ByteArray, locale: Locale): ImportReport = review(file, locale).report

	@Transactional
	fun confirm(actor: CurrentUser, file: ByteArray, checksum: String?, locale: Locale): ImportReport {
		val review = review(file, locale)
		// The reviewed file is the file imported: its checksum is the one the dry run gave.
		if (checksum?.trim()?.lowercase() != review.report.checksum) throw ApiException(ErrorCode.IMPORT_FILE_CHANGED)
		if (review.report.controlMatches == false) throw ApiException(ErrorCode.IMPORT_CONTROL_MISMATCH)

		// The review kept only rows whose unit is an active value, so each one has its id.
		val units = review.toCreate.map { it.unit }.toSet().associateWith { catalogs.activeItemId(ProductRules.UNIT_CATALOG, it)!! }
		val now = clock.instant()
		products.saveAll(review.toCreate.map { Product(it.code, it.name, units.getValue(it.unit), it.code, it.legacyId, actor.id, now, now) })
		val report = review.report.copy(created = review.toCreate.size)
		audit.record(
			actor.actor, "PRODUCTS_IMPORTED", "product_import", report.checksum,
			after = mapOf("created" to report.created, "existing" to report.existingProducts, "rejected" to report.rejected.size),
		)
		return report
	}

	private fun review(file: ByteArray, locale: Locale): Review {
		val checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file))
		val legacy = LegacyProductFile.parse(XlsxReader.read(file))
		val rejected = legacy.rejected.toMutableList()

		// What the file cannot know: the units that are active, and the products that exist.
		val activeUnits = legacy.products.map { it.unit }.toSet().filter { catalogs.activeItemId(ProductRules.UNIT_CATALOG, it) != null }.toSet()
		val existing = products.findByCodeIn(legacy.products.map { it.code }).associateBy { it.code }
		val barcodes = products.findByBarcodeInAndActiveTrue(legacy.products.map { it.code }).associateBy { it.barcode }
		val legacyIds = products.findByLegacyIdIn(legacy.products.map { it.legacyId }).associateBy { it.legacyId }
		val accepted = mutableListOf<LegacyProduct>()
		for (product in legacy.products) {
			val failure = when {
				product.unit !in activeUnits -> "unit" to ErrorCode.CATALOG_VALUE_INVALID
				// A product that exists is left as it is. Another product with its barcode or its legacy id is a conflict.
				product.code !in existing && barcodes[product.code] != null -> "barcode" to ErrorCode.DUPLICATE_VALUE
				legacyIds[product.legacyId]?.let { it.code != product.code } == true -> "legacyId" to ErrorCode.DUPLICATE_VALUE
				else -> null
			}
			if (failure == null) accepted += product else rejected += RejectedRow(product.row, failure.first, failure.second)
		}
		val toCreate = accepted.filter { it.code !in existing }
		val report = ImportReport(
			checksum = checksum,
			accepted = accepted.size,
			newProducts = toCreate.size,
			existingProducts = accepted.size - toCreate.size,
			rejected = rejected.sortedBy { it.row }.map { RejectedRowView(it.row, it.field, it.code.name, messages.getMessage(it.code.messageKey, null, locale)) },
			stockTotal = legacy.stockTotal,
			controlTotal = legacy.controlTotal,
			controlMatches = legacy.controlTotal?.let { legacy.stockTotal.compareTo(BigDecimal(it)) == 0 },
		)
		return Review(report, toCreate)
	}
}
