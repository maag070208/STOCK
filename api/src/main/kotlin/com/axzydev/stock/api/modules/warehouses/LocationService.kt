package com.axzydev.stock.api.modules.warehouses

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

// The rack form: a warehouse, an aisle, a rack, and how many levels (locations.md section 2).
data class CreateRackRequest(val warehouseId: UUID? = null, val aisle: String? = null, val rack: String? = null, val levels: Int? = null)

data class LocationView(
	val id: UUID,
	val warehouseId: UUID,
	val warehouseCode: String,
	val code: String,
	val aisle: String?,
	val rack: String?,
	val level: String?,
	val temporary: Boolean,
	val active: Boolean,
)

data class RackView(val locations: List<LocationView>)

// What the warehouses module asks of stock, without depending on it: whether locations hold any stock.
// The stock module answers it, so a location with stock is not deactivated (locations.md section 6).
fun interface LocationStock {
	fun withStock(locationIds: Collection<UUID>): Set<UUID>
}

// What stock needs of a location: its warehouse, whether it can receive (it and its warehouse active), and if it is the temporary one.
data class StockTarget(val id: UUID, val warehouseId: UUID, val code: String, val receives: Boolean, val temporary: Boolean)

@Service
class LocationService(
	private val locations: LocationRepository,
	private val warehouses: WarehouseService,
	private val stock: LocationStock,
	private val audit: AuditService,
	private val clock: Clock,
) {
	private val table = TableSpec<Location>(
		filters = mapOf(
			"warehouseId" to TableSpec.id("warehouse.id"),
			"code" to TableSpec.contains("code"),
			"aisle" to TableSpec.code("aisle"),
			"rack" to TableSpec.code("rack"),
			"active" to TableSpec.flag("active"),
			"temporary" to TableSpec.flag("temporary"),
		),
		sorts = mapOf("code" to "code", "warehouse" to "warehouse.code", "aisle" to "aisle", "rack" to "rack"),
		defaultSort = Sort.by("warehouse.code", "code"),
	)

	// With AREA, a list holds only the locations of the person's warehouses, and its total counts only those.
	@Transactional(readOnly = true)
	fun query(user: CurrentUser, request: TableRequest): TablePage<LocationView> = locations.table(request, table, scope(user), ::view)

	@Transactional(readOnly = true)
	fun get(user: CurrentUser, id: UUID): LocationView {
		val location = find(id)
		user.requireRead(WarehouseService.VIEW, location.warehouse.id)
		return view(location)
	}

	// Creates every level of a rack, or nothing: one code that exists rejects the whole request and names the codes that exist.
	@Transactional
	fun createRack(actor: CurrentUser, request: CreateRackRequest): RackView {
		val check = Validator()
		if (request.warehouseId == null) check.reject("warehouseId", ErrorCode.FIELD_REQUIRED)
		val aisle = check.required("aisle", request.aisle, WarehouseRules::aisle) { it.uppercase() }
		val rack = check.required("rack", request.rack, WarehouseRules::rack)
		when (val levels = request.levels) {
			null -> check.reject("levels", ErrorCode.FIELD_REQUIRED)
			else -> WarehouseRules.levels(levels)?.let { check.reject("levels", it) }
		}
		check.check()
		val warehouse = warehouses.activeWarehouse("warehouseId", request.warehouseId)

		val codes = WarehouseRules.rackCodes(aisle, rack, request.levels!!)
		val existing = locations.findByWarehouseIdAndCodeIn(warehouse.id!!, codes)
		if (existing.isNotEmpty()) {
			throw ValidationException(existing.map { it.code }.sorted().map { FieldError("codes.$it", ErrorCode.DUPLICATE_VALUE) })
		}

		val now = clock.instant()
		val created = locations.saveAll(
			codes.map { code -> Location(warehouse, code, aisle, rack, code.takeLast(1), temporary = false, createdBy = actor.id, createdAt = now, updatedAt = now) },
		)
		audit.record(actor.actor, "RACK_CREATED", "warehouse", warehouse.id.toString(), after = mapOf("aisle" to aisle, "rack" to rack, "codes" to codes))
		return RackView(created.map(::view))
	}

	// A location is never deleted. The temporary location belongs to the system and stays active,
	// and a location with stock is not deactivated.
	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID): LocationView {
		val location = find(id)
		if (location.temporary) throw ApiException(ErrorCode.SYSTEM_LOCATION_PROTECTED)
		if (stock.withStock(listOf(id)).isNotEmpty()) throw ApiException(ErrorCode.LOCATION_HAS_STOCK)
		return setActive(actor, location, false, "LOCATION_DEACTIVATED")
	}

	// A rack deactivates all its levels at once, and only when every level is empty (locations.md, question 2).
	@Transactional
	fun deactivateRack(actor: CurrentUser, warehouseId: UUID, aisle: String, rack: String): RackView {
		val levels = locations.findByWarehouseIdAndAisleAndRack(warehouseId, aisle.uppercase(), rack)
		if (levels.isEmpty()) throw ApiException(ErrorCode.NOT_FOUND)
		if (stock.withStock(levels.map { it.id!! }).isNotEmpty()) throw ApiException(ErrorCode.LOCATION_HAS_STOCK)
		val now = clock.instant()
		levels.filter { it.active }.forEach {
			it.active = false
			it.updatedAt = now
		}
		audit.record(actor.actor, "RACK_DEACTIVATED", "warehouse", warehouseId.toString(), after = mapOf("aisle" to aisle.uppercase(), "rack" to rack))
		return RackView(levels.sortedBy { it.code }.map(::view))
	}

	// A location of a deactivated warehouse stays inactive.
	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID): LocationView {
		val location = find(id)
		if (!location.warehouse.active) throw ValidationException(listOf(FieldError("warehouseId", ErrorCode.CATALOG_VALUE_INVALID)))
		return setActive(actor, location, true, "LOCATION_REACTIVATED")
	}

	// For other modules: the locations a set of movements names, with what stock needs of each.
	@Transactional(readOnly = true)
	fun stockTargets(ids: Collection<UUID>): Map<UUID, StockTarget> =
		locations.findAllById(ids.toSet()).associate {
			it.id!! to StockTarget(it.id!!, it.warehouse.id!!, it.code, receives = it.active && it.warehouse.active, temporary = it.temporary)
		}

	// For labels: what a location's label prints, and the warehouse it belongs to.
	@Transactional(readOnly = true)
	fun labelData(id: UUID): Pair<UUID, Map<String, String>>? = locations.findById(id).orElse(null)?.let {
		it.warehouse.id!! to mapOf(
			"code" to it.code, "warehouse_code" to it.warehouse.code,
			"aisle" to it.aisle.orEmpty(), "rack" to it.rack.orEmpty(), "level" to it.level.orEmpty(),
		)
	}

	private fun setActive(actor: CurrentUser, location: Location, active: Boolean, action: String): LocationView {
		if (location.active != active) {
			location.active = active
			location.updatedAt = clock.instant()
			audit.record(actor.actor, action, "location", location.id.toString(), after = mapOf("warehouseId" to location.warehouse.id.toString(), "code" to location.code))
		}
		return view(location)
	}

	private fun find(id: UUID): Location = locations.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun scope(user: CurrentUser): Specification<Location>? =
		when (user.scopeOf(WarehouseService.VIEW)) {
			Scope.ALL -> null
			else -> Specification { root, _, builder ->
				if (user.warehouseIds.isEmpty()) builder.disjunction()
				else root.get<Warehouse>("warehouse").get<UUID>("id").`in`(user.warehouseIds)
			}
		}

	private fun view(location: Location) = LocationView(
		id = location.id!!,
		warehouseId = location.warehouse.id!!,
		warehouseCode = location.warehouse.code,
		code = location.code,
		aisle = location.aisle,
		rack = location.rack,
		level = location.level,
		temporary = location.temporary,
		active = location.active,
	)
}
