package com.axzydev.stock.api.modules.warehouses

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "warehouses")
class Warehouse(
	var code: String,
	var name: String,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

@Entity
@Table(name = "locations")
class Location(
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "warehouse_id")
	var warehouse: Warehouse,
	var code: String,
	var aisle: String?,
	var rack: String?,
	var level: String?,
	var temporary: Boolean,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

// One rack of an aisle, with how many of its locations exist and how many are active.
interface RackGroup {
	val aisle: String
	val rack: String
	val locations: Long
	val active: Long
}

interface WarehouseRepository : JpaRepository<Warehouse, UUID>, JpaSpecificationExecutor<Warehouse> {
	fun findByCode(code: String): Warehouse?
}

interface LocationRepository : JpaRepository<Location, UUID>, JpaSpecificationExecutor<Location> {
	fun findByWarehouseIdAndCodeIn(warehouseId: UUID, codes: Collection<String>): List<Location>

	fun findByWarehouseIdAndTemporaryTrue(warehouseId: UUID): Location?

	fun findByWarehouseIdAndAisleAndRack(warehouseId: UUID, aisle: String, rack: String): List<Location>

	@Query(
		"""
		select l.aisle as aisle, l.rack as rack, count(l) as locations, sum(case when l.active then 1 else 0 end) as active
		from Location l where l.warehouse.id = :warehouseId and l.temporary = false
		group by l.aisle, l.rack order by l.aisle, l.rack
		""",
	)
	fun groups(warehouseId: UUID): List<RackGroup>
}
