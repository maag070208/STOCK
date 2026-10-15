package com.axzydev.stock.api.modules.reports

import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.time.BusinessTime
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// The reports (docs/modules/reports.md). They read the views the modules publish for lists — the same rows the stock
// screens show — and they never write. The scope is applied before the totals, and a warehouse outside it answers 404.
@Service
class ReportsService(
	private val jdbc: NamedParameterJdbcTemplate,
	private val products: ProductService,
	private val catalogs: CatalogService,
	private val warehouses: WarehouseService,
	private val people: UserService,
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


	// The kardex of one material in one warehouse (F6-02): every movement in order, oldest first, with two running
	// balances — the one of the location it touched, and the one of the material in the warehouse. A period starts from
	// what the material already had, so a kardex over everything ends at the current balance.
	@Transactional(readOnly = true)
	fun kardex(user: CurrentUser, request: TableRequest, language: String): KardexPage {
		val filters = kardexFilters(request)
		val scoped = scope(user, filters.warehouseId)
		if (scoped != null && filters.warehouseId !in scoped) throw ApiException(ErrorCode.NOT_FOUND)
		val product = products.stockProducts(listOf(filters.productId))[filters.productId] ?: throw ApiException(ErrorCode.NOT_FOUND)
		val warehouseCode = warehouses.codes(listOf(filters.warehouseId))[filters.warehouseId] ?: throw ApiException(ErrorCode.NOT_FOUND)
		val found = movements(filters, language)
		val (byLocation, warehouseOpening) = openings(filters)
		val running = KardexRules.running(found, byLocation, warehouseOpening)
		val limit = limit(request)
		val page = page(request)
		return KardexPage(
			product = KardexProduct(
				ReportRef(product.id, product.code, product.name),
				ReportRef(filters.warehouseId, warehouseCode, null),
				units(listOf(product.unitId), language)[product.unitId],
				running.lastOrNull()?.runningWarehouse ?: warehouseOpening,
			),
			data = running.drop((page - 1) * limit).take(limit),
			total = running.size.toLong(),
			page = page,
			pageIndex = page - 1,
			totalPages = pages(running.size, limit),
			totalCount = running.size.toLong(),
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < pages(running.size, limit),
		)
	}

	// The movements of the material in the warehouse, in the order they happened, with the names of what they touch.
	private fun movements(filters: KardexFilters, language: String): List<MovementReportRow> {
		val where = StringBuilder(" where m.product_id = :product and m.warehouse_id = :warehouse")
		val params = MapSqlParameterSource().addValue("product", filters.productId).addValue("warehouse", filters.warehouseId)
		filters.from?.let { where.append(" and m.occurred_at >= :from"); params.addValue("from", Timestamp.from(it)) }
		filters.to?.let { where.append(" and m.occurred_at < :to"); params.addValue("to", Timestamp.from(it)) }
		val raw = jdbc.query("select m.* from stock_movement_rows m$where order by m.seq", params) { rs, _ ->
			RawMovement(
				seq = rs.getLong("seq"),
				occurredAt = rs.getTimestamp("occurred_at").toInstant(),
				typeId = rs.getObject("type_id", UUID::class.java),
				warehouseId = rs.getObject("warehouse_id", UUID::class.java),
				warehouseCode = rs.getString("warehouse_code"),
				warehouseName = rs.getString("warehouse_name"),
				locationId = rs.getObject("location_id", UUID::class.java),
				locationCode = rs.getString("location_code"),
				productId = rs.getObject("product_id", UUID::class.java),
				productCode = rs.getString("product_code"),
				productName = rs.getString("product_name"),
				unitId = rs.getObject("unit_id", UUID::class.java),
				quantity = rs.getBigDecimal("quantity"),
				direction = rs.getString("direction"),
				userId = rs.getObject("user_id", UUID::class.java),
				reason = rs.getString("reason"),
				sourceType = rs.getString("source_type"),
				sourceId = rs.getString("source_id"),
			)
		}
		val types = catalogs.itemViews(raw.map { it.typeId }.toSet(), language)
		val unitsById = units(raw.map { it.unitId }, language)
		val peopleById = people.people(raw.mapNotNull { it.userId })
		return raw.map { row ->
			MovementReportRow(
				seq = row.seq,
				occurredAt = row.occurredAt,
				type = types[row.typeId]?.code,
				typeLabel = types[row.typeId]?.label,
				warehouse = ReportRef(row.warehouseId, row.warehouseCode, row.warehouseName),
				location = ReportRef(row.locationId, row.locationCode, null),
				product = ReportRef(row.productId, row.productCode, row.productName),
				unit = unitsById[row.unitId],
				quantity = row.quantity,
				incoming = row.direction != "out",
				user = row.userId?.let { person -> peopleById[person]?.let { ReportRef(it.id, it.username, it.name) } },
				reason = row.reason,
				sourceType = row.sourceType,
				sourceId = row.sourceId,
			)
		}
	}

	// What the material had before the first listed movement: per location, and in the warehouse.
	private fun openings(filters: KardexFilters): Pair<Map<UUID, BigDecimal>, BigDecimal> {
		val where = StringBuilder(" where m.product_id = :product and m.warehouse_id = :warehouse")
		val params = MapSqlParameterSource().addValue("product", filters.productId).addValue("warehouse", filters.warehouseId)
		// Only a kardex that starts later has an opening; without a start there is nothing before it.
		if (filters.from == null) return emptyMap<UUID, BigDecimal>() to BigDecimal.ZERO
		where.append(" and m.occurred_at < :from")
		params.addValue("from", Timestamp.from(filters.from))
		val byLocation = jdbc.query(
			"""
			select m.location_id, sum(case when m.direction = 'out' then -m.quantity else m.quantity end) as balance
			from stock_movement_rows m$where
			group by m.location_id
			""".trimIndent(),
			params,
		) { rs, _ -> rs.getObject("location_id", UUID::class.java) to rs.getBigDecimal("balance") }.toMap()
		return byLocation to byLocation.values.fold(BigDecimal.ZERO) { sum, value -> sum.add(value) }
	}

	private fun kardexFilters(request: TableRequest): KardexFilters {
		val filters = request.filters.orEmpty()
		val unknown = filters.keys - KARDEX_FILTERS
		if (unknown.isNotEmpty()) throw ValidationException(unknown.map { FieldError("filters.$it", ErrorCode.UNKNOWN_FIELD) })
		return KardexFilters(
			productId = filters["productId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("productId", it) }
				?: throw ValidationException(listOf(FieldError("filters.productId", ErrorCode.FIELD_REQUIRED))),
			warehouseId = filters["warehouseId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("warehouseId", it) }
				?: throw ValidationException(listOf(FieldError("filters.warehouseId", ErrorCode.FIELD_REQUIRED))),
			from = filters["from"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("from", it).atStartOfDay(zone()).toInstant() },
			to = filters["to"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("to", it).plusDays(1).atStartOfDay(zone()).toInstant() },
		)
	}

	private fun uuid(field: String, value: String): UUID =
		runCatching { UUID.fromString(value) }.getOrElse { throw ValidationException(listOf(FieldError("filters.$field", ErrorCode.FORMAT_INVALID))) }

	private fun date(field: String, value: String): LocalDate =
		runCatching { LocalDate.parse(value) }.getOrElse { throw ValidationException(listOf(FieldError("filters.$field", ErrorCode.FORMAT_INVALID))) }

	private fun zone(): ZoneId = ZoneId.of(BusinessTime.ZONE)

	private data class KardexFilters(val productId: UUID, val warehouseId: UUID, val from: Instant? = null, val to: Instant? = null)

	private data class RawMovement(
		val seq: Long,
		val occurredAt: Instant,
		val typeId: UUID,
		val warehouseId: UUID,
		val warehouseCode: String,
		val warehouseName: String?,
		val locationId: UUID,
		val locationCode: String,
		val productId: UUID,
		val productCode: String,
		val productName: String,
		val unitId: UUID,
		val quantity: BigDecimal,
		val direction: String?,
		val userId: UUID?,
		val reason: String?,
		val sourceType: String,
		val sourceId: String,
	)

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
		val KARDEX_FILTERS = setOf("productId", "warehouseId", "from", "to")
	}
}
