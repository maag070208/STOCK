package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.ProductStock
import com.axzydev.stock.api.modules.warehouses.LocationService
import com.axzydev.stock.api.modules.warehouses.LocationStock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

// Answers the warehouses module: which of these locations hold any stock.
@Component
class LocationStockCheck(private val jdbc: JdbcTemplate) : LocationStock {
	override fun withStock(locationIds: Collection<UUID>): Set<UUID> {
		if (locationIds.isEmpty()) return emptySet()
		return jdbc.queryForList(
			"select distinct location_id from stock_balances where quantity > 0 and location_id = any(?)",
			UUID::class.java, locationIds.toTypedArray(),
		).filterNotNull().toSet()
	}
}

// Answers the products module: the stock of each product that this person may see, in their warehouses.
@Component
class ProductStockTotals(private val jdbc: JdbcTemplate) : ProductStock {
	override fun visibleTotals(user: CurrentUser, productIds: Collection<UUID>): Map<UUID, BigDecimal>? {
		val scope = user.scopeOf(StockService.VIEW)
		if (scope == Scope.NONE) return null
		if (productIds.isEmpty()) return emptyMap()
		val all = scope == Scope.ALL
		val sql = "select product_id, sum(quantity) as quantity from stock_balances where product_id = any(?)" +
			(if (all) "" else " and warehouse_id = any(?)") + " group by product_id"
		val args: Array<Any> = if (all) arrayOf(productIds.toTypedArray()) else arrayOf(productIds.toTypedArray(), user.warehouseIds.toTypedArray())
		return jdbc.query(sql, { rs, _ -> rs.getObject("product_id", UUID::class.java) to rs.getBigDecimal("quantity") }, *args).toMap()
	}
}

// One movement to apply. `type` is a MOVEMENT_TYPE code; `sourceType` and `sourceId` name the document that causes it.
data class MovementCommand(
	val type: String,
	val locationId: UUID,
	val productId: UUID,
	val quantity: BigDecimal,
	val reason: String?,
	val sourceType: String,
	val sourceId: String,
)

data class CodeLabel(val code: String, val label: String?)

data class SourceView(val type: String, val id: String)

data class MovementView(
	val id: UUID,
	val seq: Long?,
	val type: CodeLabel,
	val warehouseId: UUID,
	val locationId: UUID,
	val locationCode: String?,
	val productId: UUID,
	val productCode: String?,
	val quantity: BigDecimal,
	val userId: UUID?,
	val occurredAt: Instant,
	val reason: String?,
	val source: SourceView,
)

// The only way stock changes (api.md section 4). Each movement is written with its balance in one transaction.
// Every balance a call touches is locked before it is read, in a fixed order, so two calls never lose an update and
// never wait on each other in a circle. A movement out above the balance is refused, and the database refuses a
// negative balance as a last guard.
@Service
class StockService(
	private val movements: StockMovementRepository,
	private val jdbc: JdbcTemplate,
	private val catalogs: CatalogService,
	private val locations: LocationService,
	private val products: ProductService,
	private val clock: Clock,
) {
	@Transactional
	fun apply(actor: Actor, commands: List<MovementCommand>): List<MovementView> {
		val types = commands.map { it.type }.toSet().associateWith { code ->
			val (id, attributes) = catalogs.activeItem(TYPE_CATALOG, code) ?: throw IllegalArgumentException("unknown movement type $code")
			id to (StockRules.direction(attributes) ?: throw IllegalStateException("movement type $code has no direction"))
		}
		val targets = locations.stockTargets(commands.map { it.locationId })
		val items = products.stockProducts(commands.map { it.productId })
		val errors = mutableListOf<FieldError>()
		for (command in commands) {
			val target = targets[command.locationId]
			val product = items[command.productId]
			val direction = types.getValue(command.type).second
			when {
				target == null -> errors += FieldError("locationId", ErrorCode.CATALOG_VALUE_INVALID)
				// Only active locations receive stock. The temporary location receives the opening inventory and nothing else.
				direction == StockRules.Direction.IN && (!target.receives || target.temporary != (command.type == OPENING)) ->
					errors += FieldError("locationId", ErrorCode.CATALOG_VALUE_INVALID)
			}
			if (product == null) errors += FieldError("productId", ErrorCode.CATALOG_VALUE_INVALID)
			else StockRules.quantity(command.quantity, product.decimals)?.let { errors += FieldError("quantity", it) }
		}
		if (errors.isNotEmpty()) throw ValidationException(errors.distinct())

		val now = clock.instant()
		val balances = lock(commands.map { it.locationId to it.productId }.toSet(), targets, now)
		val written = commands.map { command ->
			val key = command.locationId to command.productId
			val direction = types.getValue(command.type).second
			balances[key] = StockRules.apply(balances.getValue(key), direction, command.quantity)
			StockMovement(
				UUID.randomUUID(), types.getValue(command.type).first, targets.getValue(command.locationId).warehouseId,
				command.locationId, command.productId, command.quantity, actor.id, now, command.reason, command.sourceType, command.sourceId,
			)
		}
		for ((key, quantity) in balances) {
			jdbc.update(
				"update stock_balances set quantity = ?, updated_at = ? where location_id = ? and product_id = ?",
				quantity, Timestamp.from(now), key.first, key.second,
			)
		}
		movements.saveAllAndFlush(written)
		return views(written, "es", targets.mapValues { it.value.code }, items.mapValues { it.value.code })
	}

	// A movement by id, inside the reader's warehouses.
	@Transactional(readOnly = true)
	fun movement(user: CurrentUser, id: UUID, language: String): MovementView {
		val movement = movements.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }
		user.requireRead(VIEW, movement.warehouseId)
		val location = locations.stockTargets(listOf(movement.locationId)).mapValues { it.value.code }
		val product = products.stockProducts(listOf(movement.productId)).mapValues { it.value.code }
		return views(listOf(movement), language, location, product).single()
	}

	// Creates the balance rows that do not exist yet, then locks every row in a fixed order and reads it.
	private fun lock(keys: Set<Pair<UUID, UUID>>, targets: Map<UUID, com.axzydev.stock.api.modules.warehouses.StockTarget>, now: Instant): MutableMap<Pair<UUID, UUID>, BigDecimal> {
		val ordered = keys.sortedWith(compareBy({ it.first.toString() }, { it.second.toString() }))
		val balances = linkedMapOf<Pair<UUID, UUID>, BigDecimal>()
		for ((location, product) in ordered) {
			jdbc.update(
				"insert into stock_balances (location_id, product_id, warehouse_id, quantity, updated_at) values (?, ?, ?, 0, ?) on conflict do nothing",
				location, product, targets.getValue(location).warehouseId, Timestamp.from(now),
			)
			balances[location to product] = jdbc.queryForObject(
				"select quantity from stock_balances where location_id = ? and product_id = ? for update",
				BigDecimal::class.java, location, product,
			)!!
		}
		return balances
	}

	private fun views(list: List<StockMovement>, language: String, locationCodes: Map<UUID, String>, productCodes: Map<UUID, String>): List<MovementView> {
		val types = catalogs.itemViews(list.map { it.typeId }, language)
		return list.map {
			MovementView(
				id = it.id, seq = it.seq,
				type = CodeLabel(types[it.typeId]?.code ?: "", types[it.typeId]?.label),
				warehouseId = it.warehouseId, locationId = it.locationId, locationCode = locationCodes[it.locationId],
				productId = it.productId, productCode = productCodes[it.productId],
				quantity = it.quantity, userId = it.userId, occurredAt = it.occurredAt, reason = it.reason,
				source = SourceView(it.sourceType, it.sourceId),
			)
		}
	}

	companion object {
		const val VIEW = "stock.view"
		const val TYPE_CATALOG = "MOVEMENT_TYPE"
		const val OPENING = "OPENING"
		const val RECEIPT = "RECEIPT"
		const val OUT = "OUT"
		const val IN_RETURN = "IN_RETURN"
	}
}
