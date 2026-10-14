package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.files.XlsxReader
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.modules.products.LegacyProductFile
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.RejectedRowView
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.context.MessageSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

// What the opening inventory found, and, once confirmed, how many movements it wrote.
data class OpeningReport(
	val checksum: String,
	val products: Int,
	val withStock: Int,
	val total: BigDecimal,
	val controlTotal: Long?,
	val controlMatches: Boolean?,
	val missing: List<MissingProduct>,
	val rejected: List<RejectedRowView>,
	// True when the warehouse already has its opening inventory: a confirmation then writes nothing.
	val alreadyLoaded: Boolean,
	val movements: Int? = null,
)

// The opening inventory (F4, products.md section 5): the legacy file's stock goes into the warehouse's TEMPORARY
// location as OPENING movements. A dry run first, writing nothing; then the confirmation of the same file, in one
// transaction, only when the total matches the control total. A warehouse is loaded once: a second run writes nothing.
@Service
class OpeningInventoryService(
	private val stock: StockService,
	private val movements: StockMovementRepository,
	private val products: ProductService,
	private val warehouses: WarehouseService,
	private val audit: AuditService,
	private val messages: MessageSource,
	private val jdbc: JdbcTemplate,
) {
	@Transactional(readOnly = true)
	fun dryRun(file: ByteArray, warehouseId: UUID?, locale: Locale): OpeningReport = review(file, warehouseId, locale).first

	@Transactional
	fun confirm(actor: CurrentUser, file: ByteArray, warehouseId: UUID?, checksum: String?, locale: Locale): OpeningReport {
		val temporary = warehouses.temporaryLocation("warehouseId", warehouseId)
		// Two confirmations at the same moment load the warehouse once: the second waits here, then finds it loaded.
		jdbc.queryForList("select id from warehouses where id = ? for update", warehouseId)
		val (report, plan) = review(file, warehouseId, locale)
		if (checksum?.trim()?.lowercase() != report.checksum) throw ApiException(ErrorCode.IMPORT_FILE_CHANGED)
		if (report.controlMatches == false) throw ApiException(ErrorCode.IMPORT_CONTROL_MISMATCH)
		if (report.alreadyLoaded) return report.copy(movements = 0)

		val reason = "Inventario inicial"
		stock.apply(actor.actor, plan.lines.map { MovementCommand(StockService.OPENING, temporary, it.productId, it.quantity, reason, SOURCE, report.checksum) })
		audit.record(
			actor.actor, "OPENING_INVENTORY_LOADED", "warehouse", warehouseId.toString(),
			after = mapOf("movements" to plan.lines.size, "total" to report.total, "checksum" to report.checksum),
		)
		return report.copy(movements = plan.lines.size)
	}

	private fun review(file: ByteArray, warehouseId: UUID?, locale: Locale): Pair<OpeningReport, OpeningPlan> {
		warehouses.temporaryLocation("warehouseId", warehouseId)
		val checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file))
		val legacy = LegacyProductFile.parse(XlsxReader.read(file))
		val plan = OpeningInventory.plan(legacy, products.idsByCode(legacy.products.map { it.code }))
		val report = OpeningReport(
			checksum = checksum,
			products = plan.products,
			withStock = plan.lines.size,
			total = plan.total,
			controlTotal = plan.controlTotal,
			controlMatches = plan.controlMatches,
			missing = plan.missing,
			rejected = legacy.rejected.map { RejectedRowView(it.row, it.field, it.code.name, messages.getMessage(it.code.messageKey, null, locale)) },
			alreadyLoaded = movements.existsBySourceTypeAndWarehouseId(SOURCE, warehouseId!!),
		)
		return report to plan
	}

	companion object {
		const val SOURCE = "opening_inventory"
	}
}
