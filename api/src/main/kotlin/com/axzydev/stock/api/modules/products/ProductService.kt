package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TableFilter
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import com.axzydev.stock.api.modules.catalogs.CatalogItemView
import com.axzydev.stock.api.modules.catalogs.CatalogService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

// `unit` is the code of a UNIT_OF_MEASURE value. Without a barcode, the code is the barcode.
data class CreateProductRequest(val code: String? = null, val name: String? = null, val unit: String? = null, val barcode: String? = null)

// The code is not here: it cannot change. A blank barcode goes back to the code.
data class UpdateProductRequest(val name: String? = null, val unit: String? = null, val barcode: String? = null)

data class UnitView(val code: String, val label: String?, val decimalPlaces: Int?)

// What stock needs of a product: its code, and how many decimals its unit allows (0 when the unit says nothing).
data class StockProduct(val id: UUID, val code: String, val name: String, val unitId: UUID, val active: Boolean, val decimals: Int)

data class ProductView(
	val id: UUID,
	val code: String,
	val name: String,
	val barcode: String,
	val unit: UnitView,
	val legacyId: Int?,
	val active: Boolean,
	val createdAt: Instant,
)

@Service
class ProductService(
	private val products: ProductRepository,
	private val catalogs: CatalogService,
	private val audit: AuditService,
	private val clock: Clock,
) {
	// The unit filter takes a unit's code, as the list shows it.
	private val unitFilter: TableFilter<Product> = { root, builder, value ->
		when (val id = catalogs.itemId(ProductRules.UNIT_CATALOG, value.toString())) {
			null -> builder.disjunction()
			else -> builder.equal(root.get<UUID>("unitId"), id)
		}
	}

	private val table = TableSpec<Product>(
		filters = mapOf(
			"code" to TableSpec.contains("code"),
			"name" to TableSpec.contains("name"),
			"barcode" to TableSpec.contains("barcode"),
			"unit" to unitFilter,
			"active" to TableSpec.flag("active"),
		),
		sorts = mapOf("code" to "code", "name" to "name", "barcode" to "barcode", "createdAt" to "createdAt"),
		defaultSort = Sort.by("code"),
	)

	@Transactional(readOnly = true)
	fun query(request: TableRequest, language: String): TablePage<ProductView> =
		products.table(request, table) { it }.mapRows { rows ->
			val units = catalogs.itemViews(rows.map { it.unitId }, language)
			rows.map { view(it, units[it.unitId]) }
		}

	@Transactional(readOnly = true)
	fun get(id: UUID, language: String): ProductView = view(find(id), language)

	@Transactional
	fun create(actor: CurrentUser, request: CreateProductRequest, language: String): ProductView {
		val check = Validator()
		val code = check.required("code", request.code, ProductRules::code) { it.uppercase() }
		val name = check.required("name", request.name, ProductRules::name, ProductRules::normalizeName)
		val unitId = unit(check, request.unit, required = true)
		val barcode = if (code.isEmpty()) "" else ProductRules.barcodeOrCode(request.barcode, code).also { value -> ProductRules.barcode(value)?.let { check.reject("barcode", it) } }
		check.check()
		val duplicates = buildList {
			if (products.findByCode(code) != null) add(FieldError("code", ErrorCode.DUPLICATE_VALUE))
			if (products.existsByBarcodeAndActiveTrue(barcode)) add(FieldError("barcode", ErrorCode.DUPLICATE_VALUE))
		}
		if (duplicates.isNotEmpty()) throw ValidationException(duplicates)

		val now = clock.instant()
		val product = products.save(Product(code, name, unitId!!, barcode, null, actor.id, now, now))
		audit.record(actor.actor, "PRODUCT_CREATED", "product", product.id.toString(), after = snapshot(product))
		return view(product, language)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdateProductRequest, language: String): ProductView {
		val product = find(id)
		val before = snapshot(product)
		// A field that is sent must be valid: an empty name is a missing name.
		val check = Validator()
		val name = request.name?.let { check.required("name", it, ProductRules::name, ProductRules::normalizeName) }
		val unitId = request.unit?.let { unit(check, it, required = true) }
		val barcode = request.barcode?.let { ProductRules.barcodeOrCode(it, product.code) }?.also { value -> ProductRules.barcode(value)?.let { check.reject("barcode", it) } }
		check.check()
		if (barcode != null && product.active && products.existsByBarcodeAndActiveTrueAndIdNot(barcode, id)) {
			throw ValidationException(listOf(FieldError("barcode", ErrorCode.DUPLICATE_VALUE)))
		}
		name?.let { product.name = it }
		unitId?.let { product.unitId = it }
		barcode?.let { product.barcode = it }
		product.updatedAt = clock.instant()
		audit.record(actor.actor, "PRODUCT_UPDATED", "product", id.toString(), before, snapshot(product))
		return view(product, language)
	}

	// A product is never deleted. Deactivated, it is not offered for new records, stays in history, and frees its barcode.
	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID, language: String): ProductView {
		val product = find(id)
		if (product.active) {
			product.active = false
			product.updatedAt = clock.instant()
			audit.record(actor.actor, "PRODUCT_DEACTIVATED", "product", id.toString())
		}
		return view(product, language)
	}

	// Its barcode must still be free among active products.
	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID, language: String): ProductView {
		val product = find(id)
		if (!product.active) {
			if (products.existsByBarcodeAndActiveTrueAndIdNot(product.barcode, id)) {
				throw ValidationException(listOf(FieldError("barcode", ErrorCode.DUPLICATE_VALUE)))
			}
			product.active = true
			product.updatedAt = clock.instant()
			audit.record(actor.actor, "PRODUCT_REACTIVATED", "product", id.toString())
		}
		return view(product, language)
	}

	// For other modules: the ids of the products with these codes. A code with no product is left out.
	@Transactional(readOnly = true)
	fun idsByCode(codes: Collection<String>): Map<String, UUID> = products.findByCodeIn(codes.toSet()).associate { it.code to it.id!! }

	// For other modules: the products a set of movements or lines names, with what stock needs of each.
	@Transactional(readOnly = true)
	fun stockProducts(ids: Collection<UUID>): Map<UUID, StockProduct> {
		val found = products.findAllById(ids.toSet())
		val units = catalogs.itemViews(found.map { it.unitId }, "es")
		return found.associate { product ->
			val decimals = (units[product.unitId]?.attributes?.get("decimal_places") as? Number)?.toInt() ?: 0
			product.id!! to StockProduct(product.id!!, product.code, product.name, product.unitId, product.active, decimals)
		}
	}

	// The unit of a new value must be an active UNIT_OF_MEASURE value.
	private fun unit(check: Validator, code: String?, required: Boolean): UUID? {
		val clean = code?.trim().orEmpty()
		if (clean.isEmpty()) {
			if (required) check.reject("unit", ErrorCode.FIELD_REQUIRED)
			return null
		}
		return catalogs.activeItemId(ProductRules.UNIT_CATALOG, clean) ?: run {
			check.reject("unit", ErrorCode.CATALOG_VALUE_INVALID)
			null
		}
	}

	private fun find(id: UUID): Product = products.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun view(product: Product, language: String) = view(product, catalogs.itemViews(listOf(product.unitId), language)[product.unitId])

	private fun view(product: Product, unit: CatalogItemView?) = ProductView(
		id = product.id!!,
		code = product.code,
		name = product.name,
		barcode = product.barcode,
		unit = UnitView(unit?.code ?: "", unit?.label, (unit?.attributes?.get("decimal_places") as? Number)?.toInt()),
		legacyId = product.legacyId,
		active = product.active,
		createdAt = product.createdAt,
	)

	private fun snapshot(product: Product): Map<String, Any?> =
		mapOf("code" to product.code, "name" to product.name, "barcode" to product.barcode, "unitId" to product.unitId.toString(), "active" to product.active)
}
