package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.files.SheetRow
import java.math.BigDecimal

// A product row of the legacy file that passed every rule of the file itself.
data class LegacyProduct(val row: Int, val legacyId: Int, val code: String, val name: String, val unit: String, val stock: BigDecimal)

// A row that is not imported: the first field that failed, and why.
data class RejectedRow(val row: Int, val field: String, val code: ErrorCode)

data class LegacyFile(val products: List<LegacyProduct>, val rejected: List<RejectedRow>, val stockTotal: BigDecimal, val controlTotal: Long?)

// The legacy inventory file (products.md sections 2 and 6): one sheet, a header row, one row per product,
// and the accounting system's control total in a note below the data. Columns are found by their titles.
object LegacyProductFile {

	private val COLUMNS = mapOf(
		"legacyId" to "CIDPRODUCTO",
		"code" to "CCODIGOPRODUCTO",
		"name" to "CNOMBREPRODUCTO",
		"unit" to "CUNIDADMEDIDA",
		"stock" to "CEXISTENCIAS",
	)

	// The file's units and the UNIT_OF_MEASURE values they are.
	private val UNITS = mapOf("PZA" to "PIECE")

	// The note's label. The control total is the number right below it.
	private const val CONTROL_MARK = "CONTPAQ"

	fun parse(rows: List<SheetRow>): LegacyFile {
		val header = rows.firstOrNull { !it.blank } ?: throw ApiException(ErrorCode.IMPORT_FILE_INVALID)
		val column = COLUMNS.mapValues { (_, title) ->
			header.cells.entries.firstOrNull { it.value.trim().equals(title, ignoreCase = true) }?.key ?: throw ApiException(ErrorCode.IMPORT_FILE_INVALID)
		}
		val products = mutableListOf<LegacyProduct>()
		val rejected = mutableListOf<RejectedRow>()
		val codes = mutableSetOf<String>()
		val ids = mutableSetOf<Int>()
		var stockTotal = BigDecimal.ZERO

		for (row in rows.filter { it.number > header.number && !it.blank }) {
			val reject = { field: String, code: ErrorCode -> rejected += RejectedRow(row.number, field, code) }
			val code = row.text(column.getValue("code"))?.uppercase()
			if (code == null) {
				reject("code", ErrorCode.FIELD_REQUIRED)
				continue
			}
			// The stock of every product row counts toward the control total, whatever else fails in the row.
			val stock = row.text(column.getValue("stock"))?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
			if (stock != null) stockTotal += stock

			val name = row.text(column.getValue("name"))?.let(ProductRules::normalizeName)
			val legacyId = row.text(column.getValue("legacyId"))?.toBigDecimalOrNull()
				?.takeIf { it.signum() > 0 && it.stripTrailingZeros().scale() <= 0 && it <= BigDecimal(Int.MAX_VALUE) }?.toInt()
			val unitText = row.text(column.getValue("unit"))
			val unit = unitText?.uppercase()?.let(UNITS::get)
			val failure: Pair<String, ErrorCode>? = when {
				ProductRules.code(code) != null -> "code" to ProductRules.code(code)!!
				name == null -> "name" to ErrorCode.FIELD_REQUIRED
				ProductRules.name(name) != null -> "name" to ProductRules.name(name)!!
				legacyId == null -> "legacyId" to if (row.text(column.getValue("legacyId")) == null) ErrorCode.FIELD_REQUIRED else ErrorCode.FORMAT_INVALID
				unitText == null -> "unit" to ErrorCode.FIELD_REQUIRED
				unit == null -> "unit" to ErrorCode.CATALOG_VALUE_INVALID
				stock == null -> "stock" to ErrorCode.QUANTITY_INVALID
				// Every imported product takes its code as its barcode (barcode-scanning.md section 2).
				ProductRules.barcode(code) != null -> "barcode" to ProductRules.barcode(code)!!
				code in codes -> "code" to ErrorCode.DUPLICATE_VALUE
				legacyId in ids -> "legacyId" to ErrorCode.DUPLICATE_VALUE
				else -> null
			}
			if (failure != null) {
				reject(failure.first, failure.second)
				continue
			}
			codes += code
			ids += legacyId!!
			products += LegacyProduct(row.number, legacyId, code, name!!, unit!!, stock!!)
		}
		return LegacyFile(products, rejected, stockTotal, controlTotal(rows))
	}

	private fun controlTotal(rows: List<SheetRow>): Long? {
		val byNumber = rows.associateBy { it.number }
		for (row in rows) {
			val mark = row.cells.entries.firstOrNull { it.value.trim().equals(CONTROL_MARK, ignoreCase = true) } ?: continue
			return byNumber[row.number + 1]?.text(mark.key)?.toBigDecimalOrNull()?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toLong()
		}
		return null
	}
}
