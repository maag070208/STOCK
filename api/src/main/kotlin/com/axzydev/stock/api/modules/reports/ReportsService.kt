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
import com.axzydev.stock.api.modules.entries.InvoiceService
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.orders.OrderService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// The reports (docs/modules/reports.md). They read the views the modules publish for lists — the same rows the stock
// screens show — and they never write. The scope is applied before the totals, and a warehouse outside it answers 404.
//
// Every report pages, orders and totals in the database: the page query, the count and the aggregates share one WHERE,
// so the parts always add up to the whole, a page and its export list the same order, and no report pulls its whole
// result into memory. Text columns order with the C collation: the same byte order the in-memory comparators used.
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

		val where = StringBuilder(" where 1 = 1")
		val params = MapSqlParameterSource()
		scoped?.let { where.append(" and r.warehouse_id in (:warehouses)"); params.addValue("warehouses", it) }
		filters.warehouseId?.let { where.append(" and r.warehouse_id = :warehouse"); params.addValue("warehouse", it) }
		filters.aisle?.let { where.append(" and upper(r.aisle) = :aisle"); params.addValue("aisle", it.uppercase()) }
		filters.rack?.let { where.append(" and upper(r.rack) = :rack"); params.addValue("rack", it.uppercase()) }
		filters.location?.let { where.append(" and upper(r.location_code) like :location escape '\\'"); params.addValue("location", "%" + it.uppercase() + "%") }
		filters.product?.let {
			where.append(" and (lower(r.product_code) like :product or lower(r.product_name) like :product)")
			params.addValue("product", "%" + it.lowercase() + "%")
		}
		// The total of each material over the scoped warehouses decides its shortfall; the same filtered rows feed the
		// page and every total, so a page and its export agree and the parts add up to the whole.
		val cte = """
			with scoped as (select r.* from stock_balance_rows r$where),
			by_product as (
			    select s.*, p.minimum, t.total
			    from scoped s
			    join products p on p.id = s.product_id
			    join (select product_id, sum(quantity) as total from scoped group by product_id) t on t.product_id = s.product_id
			),
			listed as (
			    select v.*, case when v.minimum > 0 and v.total < v.minimum then v.minimum - v.total end as shortfall
			    from by_product v
			)
		""".trimIndent()
		val below = if (filters.belowMinimum) " where v.shortfall is not null" else ""
		val order = stockOrder(request)
		val paged = MapSqlParameterSource(params.values).addValue("limit", limit).addValue("offset", (page - 1) * limit)
		val raw = jdbc.query(
			"$cte select w.name as warehouse_name, v.* from listed v join warehouses w on w.id = v.warehouse_id$below order by $order limit :limit offset :offset",
			paged,
		) { rs, _ -> StockRow(rs) }
		val units = units(raw.map { it.unitId }, language)
		val rows = raw.map {
			StockReportRow(
				warehouse = ReportRef(it.warehouseId, it.warehouseCode, it.warehouseName),
				aisle = it.aisle, rack = it.rack,
				location = ReportRef(it.locationId, it.locationCode, null),
				product = ReportRef(it.productId, it.productCode, it.productName),
				unit = units[it.unitId],
				quantity = it.quantity,
				minimum = it.minimum,
				shortfall = it.shortfall,
			)
		}
		val total = jdbc.queryForObject("$cte select count(*) from listed v$below", params, Long::class.java)!!
		val quantity = jdbc.queryForObject("$cte select coalesce(sum(v.quantity), 0) from listed v$below", params, BigDecimal::class.java)!!
		val warehouseTotals = jdbc.query(
			"$cte select v.warehouse_id, v.warehouse_code, w.name as warehouse_name, sum(v.quantity) as quantity, count(*) as row_count from listed v join warehouses w on w.id = v.warehouse_id$below group by v.warehouse_id, v.warehouse_code, w.name order by v.warehouse_code collate \"C\"",
			params,
		) { rs, _ -> ReportTotal(rs.getString("warehouse_code"), rs.getString("warehouse_name") ?: rs.getString("warehouse_code"), rs.getBigDecimal("quantity"), rs.getLong("row_count")) }
		val groupTotals = jdbc.query(
			"$cte select g.group_key, sum(g.quantity) as quantity, count(*) as row_count from (select coalesce(nullif(concat(coalesce(v.aisle, ''), coalesce(v.rack, '')), ''), '-') as group_key, v.quantity from listed v$below) g group by g.group_key order by g.group_key collate \"C\"",
			params,
		) { rs, _ -> ReportTotal(rs.getString("group_key"), rs.getString("group_key"), rs.getBigDecimal("quantity"), rs.getLong("row_count")) }
		val totalPages = pages(total, limit)
		return StockReportPage(
			data = rows,
			total = total,
			page = page,
			pageIndex = page - 1,
			totalPages = totalPages,
			totalCount = total,
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < totalPages,
			quantity = quantity,
			warehouseTotals = warehouseTotals,
			groupTotals = groupTotals,
		)
	}

	private data class StockRow(
		val warehouseId: UUID, val warehouseCode: String, val warehouseName: String?,
		val aisle: String?, val rack: String?,
		val locationId: UUID, val locationCode: String,
		val productId: UUID, val productCode: String, val productName: String,
		val unitId: UUID, val quantity: BigDecimal, val minimum: BigDecimal, val shortfall: BigDecimal?,
	) {
		constructor(rs: ResultSet) : this(
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
			minimum = rs.getBigDecimal("minimum"),
			shortfall = rs.getBigDecimal("shortfall").takeIf { !rs.wasNull() },
		)
	}

	// The order of the stock report: the key that was asked for, and always the same ties, so a page and its export
	// list the same order.
	private fun stockOrder(request: TableRequest): String {
		val key = request.sort?.key ?: DEFAULT_SORT
		if (key !in STOCK_SORTS) throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		val direction = if (request.sort?.direction.equals("desc", ignoreCase = true)) "desc" else "asc"
		val column = when (key) {
			"warehouse" -> "v.warehouse_code collate \"C\""
			"location" -> "v.location_code collate \"C\""
			"product" -> "v.product_code collate \"C\""
			else -> "v.quantity"
		}
		return "$column $direction, v.warehouse_code collate \"C\" $direction, v.location_code collate \"C\" $direction, v.product_code collate \"C\" $direction"
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


	// The kardex of one material in one warehouse (F6-02): every movement in order, oldest first, with two running
	// balances — the one of the location it touched, and the one of the material in the warehouse. A period starts from
	// what the material already had, so a kardex over everything ends at the current balance. The running balances are
	// window sums over the same ordered rows: the database walks them in seq order, the page takes only its slice.
	@Transactional(readOnly = true)
	fun kardex(user: CurrentUser, request: TableRequest, language: String): KardexPage {
		val filters = kardexFilters(request)
		val scoped = scope(user, filters.warehouseId)
		if (scoped != null && filters.warehouseId !in scoped) throw ApiException(ErrorCode.NOT_FOUND)
		val product = products.stockProducts(listOf(filters.productId))[filters.productId] ?: throw ApiException(ErrorCode.NOT_FOUND)
		val warehouseCode = warehouses.codes(listOf(filters.warehouseId))[filters.warehouseId] ?: throw ApiException(ErrorCode.NOT_FOUND)
		val opening = warehouseOpening(filters)
		val limit = limit(request)
		val page = page(request)

		val where = StringBuilder(" where m.product_id = :product and m.warehouse_id = :warehouse")
		val params = MapSqlParameterSource().addValue("product", filters.productId).addValue("warehouse", filters.warehouseId)
		filters.from?.let { where.append(" and m.occurred_at >= :from"); params.addValue("from", Timestamp.from(it)) }
		filters.to?.let { where.append(" and m.occurred_at < :to"); params.addValue("to", Timestamp.from(it)) }
		val cte = """
			with history as (
			    select m.seq, m.occurred_at, m.type_id, m.warehouse_id, m.warehouse_code, m.warehouse_name,
			           m.location_id, m.location_code, m.product_id, m.product_code, m.product_name, m.unit_id,
			           m.quantity, m.direction, m.user_id, m.reason, m.source_type, m.source_id,
			           case when m.direction = 'out' then -m.quantity else m.quantity end as delta
			    from stock_movement_rows m$where
			),
			opened as (
			    ${if (filters.from != null) "select h.*, coalesce(o.opening, 0) as location_opening from history h left join (select m.location_id, sum(case when m.direction = 'out' then -m.quantity else m.quantity end) as opening from stock_movement_rows m where m.product_id = :product and m.warehouse_id = :warehouse and m.occurred_at < :from group by m.location_id) o on o.location_id = h.location_id" else "select h.*, 0 as location_opening from history h"}
			)
		""".trimIndent()
		val paged = MapSqlParameterSource(params.values).addValue("limit", limit).addValue("offset", (page - 1) * limit).addValue("opening", opening)
		val rows = movementPage(
			"$cte select o.seq, o.occurred_at, o.type_id, o.warehouse_id, o.warehouse_code, o.warehouse_name, o.location_id, o.location_code, o.product_id, o.product_code, o.product_name, o.unit_id, o.quantity, o.direction, o.user_id, o.reason, o.source_type, o.source_id, (:opening + sum(o.delta) over (order by o.seq)) as running_warehouse, (o.location_opening + sum(o.delta) over (partition by o.location_id order by o.seq)) as running_location from opened o order by o.seq limit :limit offset :offset",
			paged, language,
		)
		val total = jdbc.queryForObject("$cte select count(*) from history", params, Long::class.java)!!
		val closing = jdbc.queryForObject("$cte select :opening + coalesce(sum(delta), 0) from history", params.addValue("opening", opening), BigDecimal::class.java)!!
		val totalPages = pages(total, limit)
		return KardexPage(
			product = KardexProduct(
				ReportRef(product.id, product.code, product.name),
				ReportRef(filters.warehouseId, warehouseCode, null),
				units(listOf(product.unitId), language)[product.unitId],
				closing,
			),
			data = rows,
			total = total,
			page = page,
			pageIndex = page - 1,
			totalPages = totalPages,
			totalCount = total,
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < totalPages,
		)
	}

	// What the material had in the warehouse before the first listed movement: zero when the kardex starts at the
	// beginning, so a kardex over everything ends at the current balance.
	private fun warehouseOpening(filters: KardexFilters): BigDecimal {
		if (filters.from == null) return BigDecimal.ZERO
		return jdbc.queryForObject(
			"""
			select coalesce(sum(case when m.direction = 'out' then -m.quantity else m.quantity end), 0)
			from stock_movement_rows m
			where m.product_id = :product and m.warehouse_id = :warehouse and m.occurred_at < :from
			""".trimIndent(),
			MapSqlParameterSource().addValue("product", filters.productId).addValue("warehouse", filters.warehouseId).addValue("from", Timestamp.from(filters.from)),
			BigDecimal::class.java,
		)!!
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


	// The movements report (F6-03): the movements of the warehouses the reader reaches, in the order they happened,
	// filtered by period, material, type and user, with the totals of what came in and what went out. Every filter
	// combines with AND, and the page, the count and the totals read the same WHERE.
	@Transactional(readOnly = true)
	fun movements(user: CurrentUser, request: TableRequest, language: String): MovementsPage {
		val filters = movementFilters(request)
		val scoped = scope(user, filters.warehouseId)
		val limit = limit(request)
		val page = page(request)
		if (scoped != null && scoped.isEmpty()) {
			return MovementsPage(emptyList(), 0, page, page - 1, 0, 0, limit, false, false, BigDecimal.ZERO, BigDecimal.ZERO, emptyList())
		}

		val where = StringBuilder(" where 1 = 1")
		val params = MapSqlParameterSource()
		scoped?.let { where.append(" and m.warehouse_id in (:warehouses)"); params.addValue("warehouses", it) }
		filters.productId?.let { where.append(" and m.product_id = :product"); params.addValue("product", it) }
		filters.userId?.let { where.append(" and m.user_id = :user"); params.addValue("user", it) }
		filters.from?.let { where.append(" and m.occurred_at >= :from"); params.addValue("from", Timestamp.from(it)) }
		filters.to?.let { where.append(" and m.occurred_at < :to"); params.addValue("to", Timestamp.from(it)) }
		filters.type?.let { where.append(" and upper(m.type_code) = :type"); params.addValue("type", it.uppercase()) }
		val from = " from stock_movement_rows m$where"

		val paged = MapSqlParameterSource(params.values).addValue("limit", limit).addValue("offset", (page - 1) * limit)
		val rows = movementPage("select m.*$from order by ${movementOrder(request)} limit :limit offset :offset", paged, language)
		val total = jdbc.queryForObject("select count(*)$from", params, Long::class.java)!!
		val sums = jdbc.queryForObject(
			"select coalesce(sum(m.quantity) filter (where m.direction <> 'out'), 0) as incoming, coalesce(sum(m.quantity) filter (where m.direction = 'out'), 0) as outgoing$from",
			params,
		) { rs, _ -> rs.getBigDecimal("incoming") to rs.getBigDecimal("outgoing") }!!
		val typeTotals = jdbc.query(
			"select m.type_code, sum(m.quantity) as quantity, count(*) as row_count$from group by m.type_code order by m.type_code collate \"C\"",
			params,
		) { rs, _ -> rs.getString("type_code") to (rs.getBigDecimal("quantity") to rs.getLong("row_count")) }
		val labels = typeLabels(typeTotals.map { it.first }, language)
		val totalPages = pages(total, limit)
		return MovementsPage(
			data = rows,
			total = total,
			page = page,
			pageIndex = page - 1,
			totalPages = totalPages,
			totalCount = total,
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < totalPages,
			incoming = sums.first,
			outgoing = sums.second,
			typeTotals = typeTotals.map { (type, totals) -> ReportTotal(type, labels[type] ?: type, totals.first, totals.second) },
		)
	}

	// The order of the movements report: the key that was asked for, and the sequence as the tie, so a page and its
	// export list the same order.
	private fun movementOrder(request: TableRequest): String {
		val key = request.sort?.key ?: MOVEMENT_DEFAULT_SORT
		if (key !in MOVEMENT_SORTS) throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		val direction = if (request.sort?.direction.equals("asc", ignoreCase = true)) "asc" else "desc"
		val column = when (key) {
			"date" -> "m.occurred_at"
			"type" -> "m.type_code collate \"C\""
			"warehouse" -> "m.warehouse_code collate \"C\""
			"product" -> "m.product_code collate \"C\""
			else -> "m.quantity"
		}
		return "$column $direction, m.seq $direction"
	}

	private fun movementFilters(request: TableRequest): MovementFilters {
		val filters = request.filters.orEmpty()
		val unknown = filters.keys - MOVEMENT_FILTERS
		if (unknown.isNotEmpty()) throw ValidationException(unknown.map { FieldError("filters.$it", ErrorCode.UNKNOWN_FIELD) })
		return MovementFilters(
			warehouseId = filters["warehouseId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("warehouseId", it) },
			productId = filters["productId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("productId", it) },
			type = filters["type"]?.toString()?.takeIf { it.isNotBlank() },
			userId = filters["userId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("userId", it) },
			from = filters["from"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("from", it).atStartOfDay(zone()).toInstant() },
			to = filters["to"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("to", it).plusDays(1).atStartOfDay(zone()).toInstant() },
		)
	}

	// One page of movements, with the names of what they touch: their type and unit labels, and the person, each looked
	// up together for the whole page.
	private fun movementPage(sql: String, params: MapSqlParameterSource, language: String): List<MovementReportRow> {
		val raw = jdbc.query(sql, params) { rs, _ -> RawMovement(rs) }
		val types = catalogs.itemViews(raw.map { it.typeId }.toSet(), language)
		val unitsById = units(raw.map { it.unitId }, language)
		val peopleById = people.people(raw.mapNotNull { it.userId })
		return raw.map { row ->
			val type = types[row.typeId]
			MovementReportRow(
				seq = row.seq,
				occurredAt = row.occurredAt,
				type = type?.code,
				typeLabel = type?.label,
				warehouse = ReportRef(row.warehouseId, row.warehouseCode, row.warehouseName),
				location = ReportRef(row.locationId, row.locationCode, null),
				product = ReportRef(row.productId, row.productCode, row.productName),
				unit = unitsById[row.unitId],
				quantity = row.quantity,
				incoming = row.direction != "out",
				runningLocation = row.runningLocation,
				runningWarehouse = row.runningWarehouse,
				user = row.userId?.let { person -> peopleById[person]?.let { ReportRef(it.id, it.username, it.name) } },
				reason = row.reason,
				sourceType = row.sourceType,
				sourceId = row.sourceId,
			)
		}
	}

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
		val runningLocation: BigDecimal? = null,
		val runningWarehouse: BigDecimal? = null,
	) {
		constructor(rs: ResultSet) : this(
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
			runningLocation = if (hasColumn(rs, "running_location")) rs.getBigDecimal("running_location").takeIf { !rs.wasNull() } else null,
			runningWarehouse = if (hasColumn(rs, "running_warehouse")) rs.getBigDecimal("running_warehouse").takeIf { !rs.wasNull() } else null,
		)

		// The movements report has no running balances; the kardex does. A column that is not selected cannot be read.
		companion object {
			fun hasColumn(rs: ResultSet, name: String): Boolean = try {
				rs.findColumn(name); true
			} catch (e: Exception) {
				false
			}
		}
	}


	// The orders report (F6-04): every line of an order with what it asked, what was picked and what moved between
	// orders, filtered by warehouse, customer, status and period, with the totals and the same by status.
	@Transactional(readOnly = true)
	fun orders(user: CurrentUser, request: TableRequest, language: String): OrderReportPage {
		val filters = orderFilters(request)
		val scoped = scope(user, filters.warehouseId)
		if (scoped != null && scoped.isEmpty()) return OrderReportPage(emptyList(), 0, page(request), page(request) - 1, 0, 0, limit(request), false, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList())
		val limit = limit(request)
		val page = page(request)

		val where = StringBuilder(" where 1 = 1")
		val params = MapSqlParameterSource()
		scoped?.let { where.append(" and r.warehouse_id in (:warehouses)"); params.addValue("warehouses", it) }
		filters.customerId?.let { where.append(" and r.customer_id = :customer"); params.addValue("customer", it) }
		filters.statuses?.let { where.append(" and r.status_code in (:statuses)"); params.addValue("statuses", it) }
		filters.from?.let { where.append(" and r.created_at >= :from"); params.addValue("from", Timestamp.from(it)) }
		filters.to?.let { where.append(" and r.created_at < :to"); params.addValue("to", Timestamp.from(it)) }
		val from = " from order_report_rows r$where"

		val paged = MapSqlParameterSource(params.values).addValue("limit", limit).addValue("offset", (page - 1) * limit)
		val raw = jdbc.query("select r.*$from order by ${orderSort(request)} limit :limit offset :offset", paged) { rs, _ -> RawOrderReportRow(rs) }
		val total = jdbc.queryForObject("select count(*)$from", params, Long::class.java)!!
		val sums = jdbc.queryForObject(
			"select coalesce(sum(r.asked), 0) as asked, coalesce(sum(r.picked), 0) as picked, coalesce(sum(r.moved_out), 0) as moved_out, coalesce(sum(r.moved_in), 0) as moved_in$from",
			params,
		) { rs, _ -> listOf(rs.getBigDecimal("asked"), rs.getBigDecimal("picked"), rs.getBigDecimal("moved_out"), rs.getBigDecimal("moved_in")) }!!
		val statusTotals = jdbc.query(
			"select r.status_code, sum(r.picked) as picked, count(*) as row_count$from group by r.status_code order by r.status_code collate \"C\"",
			params,
		) { rs, _ -> rs.getString("status_code") to (rs.getBigDecimal("picked") to rs.getLong("row_count")) }
		val labels = statusLabels(statusTotals.map { it.first }, OrderService.STATUS_CATALOG, language)
		val units = units(raw.map { it.unitId }, language)
		val rows = raw.map { row ->
			OrderReportRow(
				order = ReportRef(row.orderId, row.number, null),
				warehouse = ReportRef(row.warehouseId, row.warehouseCode, row.warehouseName),
				customer = ReportRef(row.customerId, row.customerCode, row.customerName),
				status = row.statusCode,
				statusLabel = labels[row.statusCode],
				dueDate = row.dueDate,
				product = ReportRef(row.productId, row.productCode, row.productName),
				asked = row.asked,
				picked = row.picked,
				movedOut = row.movedOut,
				movedIn = row.movedIn,
				createdAt = row.createdAt,
				validatedAt = row.validatedAt,
			)
		}
		val totalPages = pages(total, limit)
		return OrderReportPage(
			data = rows,
			total = total,
			page = page,
			pageIndex = page - 1,
			totalPages = totalPages,
			totalCount = total,
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < totalPages,
			asked = sums[0],
			picked = sums[1],
			movedOut = sums[2],
			movedIn = sums[3],
			statusTotals = statusTotals.map { (status, totals) -> ReportTotal(status, labels[status] ?: status, totals.first, totals.second) },
		)
	}

	// The order of the orders report: the key that was asked for, and always the same ties, so a page and its export
	// list the same order.
	private fun orderSort(request: TableRequest): String {
		val key = request.sort?.key ?: "createdAt"
		if (key !in ORDER_REPORT_SORTS) throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		val direction = if (request.sort?.direction.equals("desc", ignoreCase = true)) "desc" else "asc"
		val column = when (key) {
			"number" -> "r.number collate \"C\""
			"customer" -> "coalesce(r.customer_name, r.customer_code) collate \"C\""
			"dueDate" -> "r.due_date"
			"status" -> "r.status_code collate \"C\""
			else -> "r.created_at"
		}
		return "$column $direction, r.number collate \"C\" $direction, r.product_code collate \"C\" $direction"
	}

	private data class RawOrderReportRow(
		val orderId: UUID, val number: String,
		val warehouseId: UUID, val warehouseCode: String, val warehouseName: String?,
		val customerId: UUID, val customerCode: String, val customerName: String,
		val statusCode: String,
		val dueDate: LocalDate, val createdAt: Instant, val validatedAt: Instant?,
		val productId: UUID, val productCode: String, val productName: String, val unitId: UUID,
		val asked: BigDecimal, val picked: BigDecimal, val movedOut: BigDecimal, val movedIn: BigDecimal,
	) {
		constructor(rs: ResultSet) : this(
			orderId = rs.getObject("order_id", UUID::class.java),
			number = rs.getString("number"),
			warehouseId = rs.getObject("warehouse_id", UUID::class.java),
			warehouseCode = rs.getString("warehouse_code"),
			warehouseName = rs.getString("warehouse_name"),
			customerId = rs.getObject("customer_id", UUID::class.java),
			customerCode = rs.getString("customer_code"),
			customerName = rs.getString("customer_name"),
			statusCode = rs.getString("status_code"),
			dueDate = rs.getDate("due_date").toLocalDate(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			validatedAt = rs.getTimestamp("validated_at")?.toInstant(),
			productId = rs.getObject("product_id", UUID::class.java),
			productCode = rs.getString("product_code"),
			productName = rs.getString("product_name"),
			unitId = rs.getObject("unit_id", UUID::class.java),
			asked = rs.getBigDecimal("asked"),
			picked = rs.getBigDecimal("picked"),
			movedOut = rs.getBigDecimal("moved_out"),
			movedIn = rs.getBigDecimal("moved_in"),
		)
	}


	// The entries report (F6-04): every line of an invoice with what was expected, counted, settled and placed.
	@Transactional(readOnly = true)
	fun invoices(user: CurrentUser, request: TableRequest, language: String): InvoiceReportPage {
		val filters = invoiceFilters(request)
		val scoped = scope(user, filters.warehouseId)
		if (scoped != null && scoped.isEmpty()) return InvoiceReportPage(emptyList(), 0, page(request), page(request) - 1, 0, 0, limit(request), false, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList())
		val limit = limit(request)
		val page = page(request)

		val where = StringBuilder(" where 1 = 1")
		val params = MapSqlParameterSource()
		scoped?.let { where.append(" and r.warehouse_id in (:warehouses)"); params.addValue("warehouses", it) }
		filters.supplierId?.let { where.append(" and r.supplier_id = :supplier"); params.addValue("supplier", it) }
		filters.statuses?.let { where.append(" and r.status_code in (:statuses)"); params.addValue("statuses", it) }
		filters.from?.let { where.append(" and r.invoice_date >= :from"); params.addValue("from", it) }
		filters.to?.let { where.append(" and r.invoice_date <= :to"); params.addValue("to", it) }
		val from = " from invoice_report_rows r$where"

		val paged = MapSqlParameterSource(params.values).addValue("limit", limit).addValue("offset", (page - 1) * limit)
		val raw = jdbc.query("select r.*$from order by ${invoiceSort(request)} limit :limit offset :offset", paged) { rs, _ -> RawInvoiceReportRow(rs) }
		val total = jdbc.queryForObject("select count(*)$from", params, Long::class.java)!!
		val sums = jdbc.queryForObject(
			"select coalesce(sum(r.expected), 0) as expected, coalesce(sum(r.counted), 0) as counted, coalesce(sum(r.difference), 0) as difference, coalesce(sum(r.placed), 0) as placed$from",
			params,
		) { rs, _ -> listOf(rs.getBigDecimal("expected"), rs.getBigDecimal("counted"), rs.getBigDecimal("difference"), rs.getBigDecimal("placed")) }!!
		val statusTotals = jdbc.query(
			"select r.status_code, sum(r.counted) as counted, count(*) as row_count$from group by r.status_code order by r.status_code collate \"C\"",
			params,
		) { rs, _ -> rs.getString("status_code") to (rs.getBigDecimal("counted") to rs.getLong("row_count")) }
		val labels = statusLabels(statusTotals.map { it.first }, InvoiceService.STATUS_CATALOG, language)
		val rows = raw.map { row ->
			InvoiceReportRow(
				invoice = ReportRef(row.invoiceId, row.number, null),
				supplier = ReportRef(row.supplierId, row.supplierCode, row.supplierName),
				warehouse = ReportRef(row.warehouseId, row.warehouseCode, row.warehouseName),
				status = row.statusCode,
				statusLabel = labels[row.statusCode],
				invoiceDate = row.invoiceDate,
				product = ReportRef(row.productId, row.productCode, row.productName),
				expected = row.expected,
				counted = row.counted,
				difference = row.difference,
				placed = row.placed,
			)
		}
		val totalPages = pages(total, limit)
		return InvoiceReportPage(
			data = rows,
			total = total,
			page = page,
			pageIndex = page - 1,
			totalPages = totalPages,
			totalCount = total,
			limit = limit,
			hasPreviousPage = page > 1,
			hasNextPage = page < totalPages,
			expected = sums[0],
			counted = sums[1],
			difference = sums[2],
			placed = sums[3],
			statusTotals = statusTotals.map { (status, totals) -> ReportTotal(status, labels[status] ?: status, totals.first, totals.second) },
		)
	}

	// The order of the entries report: the key that was asked for, and always the same ties, so a page and its export
	// list the same order.
	private fun invoiceSort(request: TableRequest): String {
		val key = request.sort?.key ?: "date"
		if (key !in INVOICE_REPORT_SORTS) throw ValidationException(listOf(FieldError("sort.key", ErrorCode.FORMAT_INVALID)))
		val direction = if (request.sort?.direction.equals("desc", ignoreCase = true)) "desc" else "asc"
		val column = when (key) {
			"number" -> "r.number collate \"C\""
			"supplier" -> "coalesce(r.supplier_name, r.supplier_code) collate \"C\""
			"status" -> "r.status_code collate \"C\""
			else -> "r.invoice_date"
		}
		return "$column $direction, r.number collate \"C\" $direction, r.product_code collate \"C\" $direction"
	}

	private data class RawInvoiceReportRow(
		val invoiceId: UUID, val number: String,
		val warehouseId: UUID, val warehouseCode: String, val warehouseName: String?,
		val supplierId: UUID, val supplierCode: String, val supplierName: String,
		val statusCode: String, val invoiceDate: LocalDate,
		val productId: UUID, val productCode: String, val productName: String,
		val expected: BigDecimal, val counted: BigDecimal, val difference: BigDecimal, val placed: BigDecimal,
	) {
		constructor(rs: ResultSet) : this(
			invoiceId = rs.getObject("invoice_id", UUID::class.java),
			number = rs.getString("number"),
			warehouseId = rs.getObject("warehouse_id", UUID::class.java),
			warehouseCode = rs.getString("warehouse_code"),
			warehouseName = rs.getString("warehouse_name"),
			supplierId = rs.getObject("supplier_id", UUID::class.java),
			supplierCode = rs.getString("supplier_code"),
			supplierName = rs.getString("supplier_name"),
			statusCode = rs.getString("status_code"),
			invoiceDate = rs.getDate("invoice_date").toLocalDate(),
			productId = rs.getObject("product_id", UUID::class.java),
			productCode = rs.getString("product_code"),
			productName = rs.getString("product_name"),
			expected = rs.getBigDecimal("expected"),
			counted = rs.getBigDecimal("counted"),
			difference = rs.getBigDecimal("difference"),
			placed = rs.getBigDecimal("placed"),
		)
	}


	private fun orderFilters(request: TableRequest): OrderFilters {
		val filters = request.filters.orEmpty()
		val unknown = filters.keys - ORDER_REPORT_FILTERS
		if (unknown.isNotEmpty()) throw ValidationException(unknown.map { FieldError("filters.$it", ErrorCode.UNKNOWN_FIELD) })
		return OrderFilters(
			warehouseId = filters["warehouseId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("warehouseId", it) },
			customerId = filters["customerId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("customerId", it) },
			statuses = statuses(filters),
			from = filters["from"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("from", it).atStartOfDay(zone()).toInstant() },
			to = filters["to"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("to", it).plusDays(1).atStartOfDay(zone()).toInstant() },
		)
	}

	private fun invoiceFilters(request: TableRequest): InvoiceFilters {
		val filters = request.filters.orEmpty()
		val unknown = filters.keys - INVOICE_REPORT_FILTERS
		if (unknown.isNotEmpty()) throw ValidationException(unknown.map { FieldError("filters.$it", ErrorCode.UNKNOWN_FIELD) })
		return InvoiceFilters(
			warehouseId = filters["warehouseId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("warehouseId", it) },
			supplierId = filters["supplierId"]?.toString()?.takeIf { it.isNotBlank() }?.let { uuid("supplierId", it) },
			statuses = statuses(filters),
			from = filters["from"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("from", it) },
			to = filters["to"]?.toString()?.takeIf { it.isNotBlank() }?.let { date("to", it) },
		)
	}

	// One status code, or several: the reports read the codes of the catalog.
	private fun statuses(filters: Map<String, Any?>): List<String>? =
		when (val value = filters["status"]) {
			null -> null
			is List<*> -> value.mapNotNull { it?.toString()?.takeIf { code -> code.isNotBlank() } }.ifEmpty { null }
			else -> value.toString().takeIf { it.isNotBlank() }?.let { listOf(it) }
		}

	// The labels of status codes in the request's language: the codes the totals name, even the ones not on this page.
	private fun statusLabels(codes: Collection<String>, catalog: String, language: String): Map<String, String?> =
		codes.toSet().associateWith { code -> catalogs.itemId(catalog, code)?.let { id -> catalogs.itemViews(listOf(id), language)[id]?.label } }

	// The labels of movement type codes in the request's language: the codes the totals name.
	private fun typeLabels(codes: Collection<String>, language: String): Map<String, String?> = statusLabels(codes, StockService.TYPE_CATALOG, language)


	private data class OrderFilters(
		val warehouseId: UUID? = null,
		val customerId: UUID? = null,
		val statuses: List<String>? = null,
		val from: Instant? = null,
		val to: Instant? = null,
	)

	private data class InvoiceFilters(
		val warehouseId: UUID? = null,
		val supplierId: UUID? = null,
		val statuses: List<String>? = null,
		val from: LocalDate? = null,
		val to: LocalDate? = null,
	)

	private data class MovementFilters(
		val warehouseId: UUID? = null,
		val productId: UUID? = null,
		val type: String? = null,
		val userId: UUID? = null,
		val from: Instant? = null,
		val to: Instant? = null,
	)

	private data class KardexFilters(val productId: UUID, val warehouseId: UUID, val from: Instant? = null, val to: Instant? = null)

	private data class StockFilters(
		val warehouseId: UUID? = null,
		val aisle: String? = null,
		val rack: String? = null,
		val location: String? = null,
		val product: String? = null,
		val belowMinimum: Boolean = false,
	)

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

	private fun units(ids: Collection<UUID>, language: String): Map<UUID, ReportUnit> =
		catalogs.itemViews(ids.toSet(), language).mapValues { (_, item) ->
			ReportUnit(item.code, item.label, (item.attributes["decimal_places"] as? Number)?.toInt())
		}

	private fun uuid(field: String, value: String): UUID =
		runCatching { UUID.fromString(value) }.getOrElse { throw ValidationException(listOf(FieldError("filters.$field", ErrorCode.FORMAT_INVALID))) }

	private fun date(field: String, value: String): LocalDate =
		runCatching { LocalDate.parse(value) }.getOrElse { throw ValidationException(listOf(FieldError("filters.$field", ErrorCode.FORMAT_INVALID))) }

	private fun zone(): ZoneId = ZoneId.of(BusinessTime.ZONE)

	private fun limit(request: TableRequest): Int = (request.limit ?: TableSpec.DEFAULT_LIMIT).coerceIn(1, TableSpec.MAX_LIMIT)

	private fun page(request: TableRequest): Int = (request.page ?: 1).coerceAtLeast(1)

	private fun pages(rows: Long, limit: Int): Int = if (rows == 0L) 0 else ((rows + limit - 1) / limit).toInt()

	companion object {
		const val VIEW = "reports.view"
		const val EXPORT = "reports.export"
		const val DEFAULT_SORT = "warehouse"
		val STOCK_FILTERS = setOf("warehouseId", "aisle", "rack", "location", "product", "belowMinimum")
		val STOCK_SORTS = setOf("warehouse", "location", "product", "quantity")
		val KARDEX_FILTERS = setOf("productId", "warehouseId", "from", "to")
		val MOVEMENT_FILTERS = setOf("warehouseId", "productId", "type", "userId", "from", "to")
		val MOVEMENT_SORTS = setOf("date", "type", "warehouse", "product", "quantity")
		const val MOVEMENT_DEFAULT_SORT = "date"
		val ORDER_REPORT_FILTERS = setOf("warehouseId", "customerId", "status", "from", "to")
		val ORDER_REPORT_SORTS = setOf("number", "customer", "dueDate", "status", "createdAt")
		val INVOICE_REPORT_FILTERS = setOf("warehouseId", "supplierId", "status", "from", "to")
		val INVOICE_REPORT_SORTS = setOf("number", "supplier", "status", "date")
	}
}
