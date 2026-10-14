package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.catalogs.CatalogService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

// What the products module asks of stock, without depending on it: each product's stock that this person may see,
// or null when they may not see stock. The stock module answers it.
fun interface ProductStock {
	fun visibleTotals(user: CurrentUser, productIds: Collection<UUID>): Map<UUID, BigDecimal>?
}

data class LookupProduct(val id: UUID, val code: String, val name: String, val barcode: String, val unit: UnitView, val stock: BigDecimal?)

// How the value was found: BARCODE, CODE, or NAME (a list the person picks from).
data class LookupResult(val match: String, val products: List<LookupProduct>)

// The lookup of a scanned or typed value (barcode-scanning.md section 3): an active product's barcode, then its code,
// then its name. Several names are a list, and nothing is chosen. The lookup never creates anything.
@Service
class ProductLookup(
	private val products: ProductRepository,
	private val catalogs: CatalogService,
	private val stock: ProductStock,
) {
	@Transactional(readOnly = true)
	fun lookup(user: CurrentUser, value: String?, language: String): LookupResult {
		val clean = value?.trim().orEmpty()
		if (clean.isEmpty()) throw ValidationException(listOf(FieldError("value", ErrorCode.FIELD_REQUIRED)))
		val upper = clean.uppercase()
		products.findByBarcodeAndActiveTrue(upper)?.let { return result(user, "BARCODE", listOf(it), language) }
		products.findByCode(upper)?.takeIf { it.active }?.let { return result(user, "CODE", listOf(it), language) }
		val pattern = "%" + clean.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
		val byName = products.findAll(
			Specification<Product> { root, _, builder ->
				builder.and(builder.isTrue(root.get("active")), builder.like(builder.lower(root.get("name")), pattern, '\\'))
			},
			PageRequest.of(0, MAX_RESULTS, Sort.by("name")),
		).content
		if (byName.isEmpty()) throw ApiException(ErrorCode.BARCODE_NOT_FOUND)
		return result(user, "NAME", byName, language)
	}

	private fun result(user: CurrentUser, match: String, found: List<Product>, language: String): LookupResult {
		val units = catalogs.itemViews(found.map { it.unitId }, language)
		val totals = stock.visibleTotals(user, found.map { it.id!! })
		return LookupResult(match, found.map { product ->
			val unit = units[product.unitId]
			LookupProduct(
				product.id!!, product.code, product.name, product.barcode,
				UnitView(unit?.code ?: "", unit?.label, (unit?.attributes?.get("decimal_places") as? Number)?.toInt()),
				totals?.let { it[product.id!!] ?: BigDecimal.ZERO },
			)
		})
	}

	companion object {
		private const val MAX_RESULTS = 50
	}
}
