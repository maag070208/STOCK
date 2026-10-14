package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.modules.products.LegacyFile
import java.math.BigDecimal
import java.util.UUID

data class OpeningLine(val productId: UUID, val code: String, val quantity: BigDecimal)

data class MissingProduct(val row: Int, val code: String)

data class OpeningPlan(
	val products: Int,
	val lines: List<OpeningLine>,
	val missing: List<MissingProduct>,
	val total: BigDecimal,
	val controlTotal: Long?,
	val controlMatches: Boolean?,
)

// The opening inventory of the legacy file (products.md section 5): one OPENING movement per product with stock.
// The total is what would be written; it must equal the file's control total, so a product that does not exist
// (its stock cannot be written) makes the control fail and the whole load is refused.
object OpeningInventory {

	fun plan(file: LegacyFile, productIds: Map<String, UUID>): OpeningPlan {
		val missing = file.products.filter { it.code !in productIds }.map { MissingProduct(it.row, it.code) }
		val lines = file.products
			.filter { it.code in productIds && it.stock.signum() > 0 }
			.map { OpeningLine(productIds.getValue(it.code), it.code, it.stock) }
		val total = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.quantity }
		return OpeningPlan(
			products = file.products.size,
			lines = lines,
			missing = missing,
			total = total,
			controlTotal = file.controlTotal,
			controlMatches = file.controlTotal?.let { total.compareTo(BigDecimal(it)) == 0 },
		)
	}
}
