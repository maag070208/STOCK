package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

// The reports (docs/modules/reports.md). They read the views the modules publish for lists — the same rows the stock
// screens show — and they never write. The scope is applied before the totals, and a warehouse outside it answers 404.
@Service
class ReportsService(
	private val jdbc: NamedParameterJdbcTemplate,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val warehouses: WarehouseService,
) {
	// The current stock (F6-01): one row per balance above zero, with the totals by warehouse, by aisle and rack, and
	// the grand total. `belowMinimum` keeps only the materials whose total in the scoped warehouses is below their
	// minimum, and marks each row with what is missing.
	@Transactional(readOnly = true)
	fun stock(user: CurrentUser, request: TableRequest, language: String): StockReportPage {
		val filters = stockFilters(request)
		val scoped = scope(user, filters.warehouseId)
		val limit = limit(request)
		val page = page(request)
		if (scoped != null && scoped.isEmpty()) return StockReportPage(emptyList(), 0, page, page - 1, 0, 0, limit, false, false, BigDecimal.ZERO, emptyList(), emptyList())

		val found = balanceRows(scoped, filters)
		val units = units(found.map { it.unitId }, language)
		val minimums = products.minimums(found.map { it.productId })
		// What each material has over the scoped warehouses: the rows themselves, so the parts and the whole agree.
		val totalsByProduct = found.groupBy { it.productId }.mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { total, row -> total.add(row.quantity) } }
		val rows = found
			.filter { row -> !filters.belowMinimum || isBelow(totalsByProduct, minimums, row.productId) }
			.map { row ->
				val minimum = minimums[row.productId] ?: BigDecimal.ZERO
				val shortfall = if (isBelow(totalsByProduct, minimums, row.productId)) minimum.subtract(totalsByProduct.getValue(row.productId)) else null
				StockReportRow(
					warehouse = ReportRef(row.warehouseId, row.warehouseCode, row.warehouseName),
					aisle = row.aisle,
					rack = row.rack,
					location = ReportRef(row.locationId, row.locationCode, null),
					product = ReportRef(row.productId, row.productCode, row.productName),
					unit = units[row.unitId],
					quantity = row.quantity,
					minimum = minimum,
					shortfall = shortfall,
				)
			}
			.sortedWith(stockOrder(request))

		val totals = StockReportRules.totals(rows)
		return StockReportPage(
			data = rows.drop((page - 1) * limit).take(limit),
			total = rows.size.toLong(),
			page = page,
			pageIndex = page - 1,
			totalPages = pages(rows.size, limit),
			totalCount = rows.size.toLong(),
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < pages(rows.size, limit),
			quantity = totals.quantity,
			warehouseTotals = totals.warehouses,
			groupTotals = totals.groups,
		)
	}

	private fun isBelow(totals: Map<UUID, BigDecimal>, minimums: Map<UUID, BigDecimal>, productId: UUID): Boolean =
		(minimums[productId] ?: BigDecimal.ZERO).signum() > 0 && (totals[productId] ?: BigDecimal.ZERO) < minimums.getValue(productId)

	// The warehouses the reader may see: theirs with AREA, every one with ALL. A warehouse that is asked for and is
	// outside the scope answers as if it did not exist (reports.md section 2).
	private fun scope(user: CurrentUser, warehouseId: UUID?): List<UUID>? {
		if (!user.can(VIEW)) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
		val allowed = if (user.scopeOf(VIEW) == Scope.ALL) null else user.warehouseIds.toList()
		if (warehouseId == null) return allowed
		if (allowed != null && warehouseId !in allowed) throw ApiException(ErrorCode.NOT_FOUND)
		if (warehouses.codes(listOf(warehouseId)).isEmpty()) throw ApiException(ErrorCode.NOT_FOUND)
		return listOf(warehouseId)
	}

	// One balance per location and material, with the warehouse and the material it belongs to.
	private fun balanceRows(scoped: List<UUID>?, filters: StockFilters): List<Row> {
		if (scoped != null && scoped.isEmpty()) return emptyList()
		val where = StringBuilder(" where 1 = 1")
		val params = MapSqlParameterSource()
		scoped?.let {
			where.append(" and r.warehouse_id in (:warehouses)")
			params.addValue("warehouses", it)
		}
		filters.aisle?.let {
			where.append(" and upper(r.aisle) = :aisle")
			params.addValue("aisle", it.uppercase())
		}
		filters.rack?.let {
			where.append(" and upper(r.rack) = :rack")
			params.addValue("rack", it.uppercase())
		}
		filters.location?.let {
			where.append(" and upper(r.location_code) like :location")
			params.addValue("location", "%" + it.uppercase() + "%")
		}
		filters.product?.let {
			where.append(" and (lower(r.product_code) like :product or lower(r.product_name) like :product)")
			params.addValue("product", "%" + it.lowercase() + "%")
		}
		return jdbc.query(
			"""
			select r.warehouse_id, r.warehouse_code, w.name as warehouse_name, r.aisle, r.rack, r.location_id, r.location_code,
			       r.product_id, r.product_code, r.product_name, r.unit_id, r.quantity
			from stock_balance_rows r join warehouses w on w.id = r.warehouse_id
			$where
			""".trimIndent(),
			params,
		) { rs, _ ->
			Row(
				warehouseId = rs.getObject("warehouse_id", UUID::class.java),
				warehouseCode = rs.getString("warehouse_code"),
				warehouseName = rs.getString("warehouse_name"),
				aisle = rs.getString("aisle"),
				rack = rs.getString("rack"),
				locationId = rs.getObject("location_id", UUID::class.java),
				locationCode = rs.getString("location_code"),
				productId = rs.getObject("product_id", UUID::class.java),
				productCode = rs.getString("product_code"),
				productName = rs.getString("product_name"),
				unitId = rs.getObject("unit_id", UUID::class.java),
				quantity = rs.getBigDecimal("quantity"),
			)
		}
	}

	private fun units(ids: Collection<UUID>, language: String): Map<UUID, ReportUnit> =
		catalogs.itemViews(ids.toSet(), language).mapValues { (_, item) ->
			ReportUnit(item.code, item.label, (item.attributes["decimal_places"] as? Number)?.toInt())
		}

	private fun stockOrder(request: TableRequest): Comparator<StockReportRow> {
		val key = request.sort?.key ?: DEFAULT_SORT
		if (key !in STOCK_SORTS) throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		val direction = if (request.sort?.direction.equals("desc", ignoreCase = true)) -1 else 1
		val comparator = when (key) {
			"warehouse" -> compareBy<StockReportRow> { it.warehouse.code }
			"location" -> compareBy { it.location.code }
			"product" -> compareBy { it.product.code }
			else -> compareBy { it.quantity }
		}
		// The ties are always broken the same way, so a page and its export list the same order.
		return (comparator.thenBy { it.warehouse.code }.thenBy { it.location.code }.thenBy { it.product.code })
			.let { if (direction < 0) it.reversed() else it }
	}

	private fun stockFilters(request: TableRequest): StockFilters {
		val filters = request.filters.orEmpty()
		val unknown = filters.keys - STOCK_FILTERS
		if (unknown.isNotEmpty()) throw ValidationException(unknown.map { FieldError("filters.$it", ErrorCode.UNKNOWN_FIELD) })
		return StockFilters(
			warehouseId = filters["warehouseId"]?.toString()?.takeIf { it.isNotBlank() }?.let {
				runCatching { UUID.fromString(it) }.getOrElse { throw ValidationException(listOf(FieldError("filters.warehouseId", ErrorCode.FORMAT_INVALID))) }
			},
			aisle = filters["aisle"]?.toString()?.takeIf { it.isNotBlank() },
			rack = filters["rack"]?.toString()?.takeIf { it.isNotBlank() },
			location = filters["location"]?.toString()?.takeIf { it.isNotBlank() },
			product = filters["product"]?.toString()?.takeIf { it.isNotBlank() },
			belowMinimum = filters["belowMinimum"]?.toString() == "true",
		)
	}

	private fun limit(request: TableRequest): Int = (request.limit ?: TableSpec.DEFAULT_LIMIT).coerceIn(1, TableSpec.MAX_LIMIT)

	private fun page(request: TableRequest): Int = (request.page ?: 1).coerceAtLeast(1)

	private fun pages(rows: Int, limit: Int): Int = if (rows == 0) 0 else (rows + limit - 1) / limit

	private data class StockFilters(
		val warehouseId: UUID? = null,
		val aisle: String? = null,
		val rack: String? = null,
		val location: String? = null,
		val product: String? = null,
		val belowMinimum: Boolean = false,
	)

	private data class Row(
		val warehouseId: UUID,
		val warehouseCode: String,
		val warehouseName: String?,
		val aisle: String?,
		val rack: String?,
		val locationId: UUID,
		val locationCode: String,
		val productId: UUID,
		val productCode: String,
		val productName: String,
		val unitId: UUID,
		val quantity: BigDecimal,
	)

	companion object {
		const val VIEW = "reports.view"
		const val EXPORT = "reports.export"
		const val DEFAULT_SORT = "warehouse"
		val STOCK_FILTERS = setOf("warehouseId", "aisle", "rack", "location", "product", "belowMinimum")
		val STOCK_SORTS = setOf("warehouse", "location", "product", "quantity")
	}
}
