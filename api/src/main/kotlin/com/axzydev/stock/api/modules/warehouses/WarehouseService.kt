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
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateWarehouseRequest(val code: String? = null, val name: String? = null)

// The code is not here: it cannot change.
data class UpdateWarehouseRequest(val name: String? = null)

data class LocationRef(val id: UUID, val code: String)

data class WarehouseView(
	val id: UUID,
	val code: String,
	val name: String,
	val active: Boolean,
	val temporaryLocation: LocationRef?,
	val createdAt: Instant,
)

data class RackGroupView(val rack: String, val locations: Long, val active: Long)

data class AisleGroupView(val aisle: String, val racks: List<RackGroupView>)

@Service
class WarehouseService(
	private val warehouses: WarehouseRepository,
	private val locations: LocationRepository,
	private val audit: AuditService,
	private val clock: Clock,
) {
	private val table = TableSpec<Warehouse>(
		filters = mapOf(
			"code" to TableSpec.contains("code"),
			"name" to TableSpec.contains("name"),
			"active" to TableSpec.flag("active"),
		),
		sorts = mapOf("code" to "code", "name" to "name", "createdAt" to "createdAt"),
		defaultSort = Sort.by("code"),
	)

	// With AREA, a list holds only the person's warehouses, and its total counts only those.
	@Transactional(readOnly = true)
	fun query(user: CurrentUser, request: TableRequest): TablePage<WarehouseView> =
		warehouses.table(request, table, scope(user), ::view)

	@Transactional(readOnly = true)
	fun get(user: CurrentUser, id: UUID): WarehouseView = view(readable(user, id))

	// The groups of a warehouse's locations, by aisle and rack. Groups store nothing of their own.
	@Transactional(readOnly = true)
	fun groups(user: CurrentUser, id: UUID): List<AisleGroupView> {
		readable(user, id)
		return locations.groups(id)
			.groupBy { it.aisle }
			.map { (aisle, racks) -> AisleGroupView(aisle, racks.map { RackGroupView(it.rack, it.locations, it.active) }) }
	}

	// A new warehouse comes with its temporary location, in the same transaction.
	@Transactional
	fun create(actor: CurrentUser, request: CreateWarehouseRequest): WarehouseView {
		val check = Validator()
		val code = check.required("code", request.code, WarehouseRules::code) { it.uppercase() }
		val name = check.required("name", request.name, Rules::name)
		check.check()
		if (warehouses.findByCode(code) != null) throw ValidationException(listOf(FieldError("code", ErrorCode.DUPLICATE_VALUE)))

		val now = clock.instant()
		val warehouse = warehouses.save(Warehouse(code, name, actor.id, now, now))
		locations.save(Location(warehouse, WarehouseRules.TEMPORARY, null, null, null, temporary = true, createdBy = actor.id, createdAt = now, updatedAt = now))
		audit.record(actor.actor, "WAREHOUSE_CREATED", "warehouse", warehouse.id.toString(), after = snapshot(warehouse))
		return view(warehouse)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdateWarehouseRequest): WarehouseView {
		val warehouse = find(id)
		val before = snapshot(warehouse)
		// A field that is sent must be valid: an empty name is a missing name.
		val check = Validator()
		val name = request.name?.let { check.required("name", it, Rules::name) }
		check.check()
		name?.let { warehouse.name = it }
		warehouse.updatedAt = clock.instant()
		audit.record(actor.actor, "WAREHOUSE_UPDATED", "warehouse", id.toString(), before, snapshot(warehouse))
		return view(warehouse)
	}

	// A warehouse is never deleted. Deactivated, it is not offered for new records and stays in history.
	@Transactional
	fun deactivate(actor: CurrentUser, id: UUID): WarehouseView = setActive(actor, id, false, "WAREHOUSE_DEACTIVATED")

	@Transactional
	fun reactivate(actor: CurrentUser, id: UUID): WarehouseView = setActive(actor, id, true, "WAREHOUSE_REACTIVATED")

	// For other modules: the warehouses a new record names must exist and be active.
	@Transactional(readOnly = true)
	fun requireActive(field: String, ids: Collection<UUID>) {
		val found = warehouses.findAllById(ids.toSet())
		if (found.size != ids.toSet().size || found.any { !it.active }) {
			throw ValidationException(listOf(FieldError(field, ErrorCode.CATALOG_VALUE_INVALID)))
		}
	}

	// For other modules: an active warehouse for a new record, or CATALOG_VALUE_INVALID on the field.
	@Transactional(readOnly = true)
	fun requireActive(check: com.axzydev.stock.api.core.validation.Validator, field: String, id: UUID?) {
		val warehouse = id?.let { warehouses.findById(it).orElse(null) }
		when {
			id == null -> check.reject(field, ErrorCode.FIELD_REQUIRED)
			warehouse == null || !warehouse.active -> check.reject(field, ErrorCode.CATALOG_VALUE_INVALID)
		}
	}

	// For other modules: the active warehouses as (id, code, name), by code, for a new record to name one.
	@Transactional(readOnly = true)
	fun activeOptions(): List<Triple<UUID, String, String>> =
		warehouses.findAll(Sort.by("code")).filter { it.active }.map { Triple(it.id!!, it.code, it.name) }

	// For other modules: every warehouse, for a reader whose scope is ALL.
	@Transactional(readOnly = true)
	fun allIds(): List<UUID> = warehouses.findAll().map { it.id!! }

	// For other modules: the code of each warehouse, for display.
	@Transactional(readOnly = true)
	fun codes(ids: Collection<UUID>): Map<UUID, String> = warehouses.findAllById(ids.toSet()).associate { it.id!! to it.code }

	// For other modules: the temporary location of an active warehouse, which holds its opening inventory.
	@Transactional(readOnly = true)
	fun temporaryLocation(field: String, id: UUID?): UUID {
		val warehouse = activeWarehouse(field, id)
		return locations.findByWarehouseIdAndTemporaryTrue(warehouse.id!!)!!.id!!
	}

	internal fun activeWarehouse(field: String, id: UUID?): Warehouse {
		val warehouse = id?.let { warehouses.findById(it).orElse(null) }
		if (warehouse == null || !warehouse.active) throw ValidationException(listOf(FieldError(field, ErrorCode.CATALOG_VALUE_INVALID)))
		return warehouse
	}

	private fun setActive(actor: CurrentUser, id: UUID, active: Boolean, action: String): WarehouseView {
		val warehouse = find(id)
		if (warehouse.active != active) {
			warehouse.active = active
			warehouse.updatedAt = clock.instant()
			audit.record(actor.actor, action, "warehouse", id.toString())
		}
		return view(warehouse)
	}

	private fun find(id: UUID): Warehouse = warehouses.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	// A read by id outside the person's warehouses answers as if the warehouse did not exist.
	private fun readable(user: CurrentUser, id: UUID): Warehouse {
		val warehouse = find(id)
		user.requireRead(VIEW, warehouse.id)
		return warehouse
	}

	private fun scope(user: CurrentUser): Specification<Warehouse>? =
		when (user.scopeOf(VIEW)) {
			Scope.ALL -> null
			else -> Specification { root, _, builder ->
				if (user.warehouseIds.isEmpty()) builder.disjunction() else root.get<UUID>("id").`in`(user.warehouseIds)
			}
		}

	private fun view(warehouse: Warehouse) = WarehouseView(
		id = warehouse.id!!,
		code = warehouse.code,
		name = warehouse.name,
		active = warehouse.active,
		temporaryLocation = locations.findByWarehouseIdAndTemporaryTrue(warehouse.id!!)?.let { LocationRef(it.id!!, it.code) },
		createdAt = warehouse.createdAt,
	)

	private fun snapshot(warehouse: Warehouse): Map<String, Any?> =
		mapOf("code" to warehouse.code, "name" to warehouse.name, "active" to warehouse.active)

	companion object {
		const val VIEW = "locations.view"
		const val MANAGE = "locations.manage"
	}
}
