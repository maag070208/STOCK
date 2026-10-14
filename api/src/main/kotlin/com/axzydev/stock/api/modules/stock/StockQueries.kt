package com.axzydev.stock.api.modules.stock

import com.axzydev.stock.api.core.db.TableFilter
import com.axzydev.stock.api.core.db.TablePage
import com.axzydev.stock.api.core.db.TableRequest
import com.axzydev.stock.api.core.db.TableSpec
import com.axzydev.stock.api.core.db.table
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.modules.catalogs.CatalogService
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BalanceKey(val locationId: UUID? = null, val productId: UUID? = null) : Serializable

// A row of stock_balance_rows (V11): one balance above zero, with its warehouse, location and material.
@Entity
@Immutable
@Table(name = "stock_balance_rows")
@IdClass(BalanceKey::class)
class BalanceRow(
	@Id val locationId: UUID,
	@Id val productId: UUID,
	val warehouseId: UUID,
	val quantity: BigDecimal,
	val updatedAt: Instant,
	val warehouseCode: String,
	val locationCode: String,
	val aisle: String?,
	val rack: String?,
	val temporary: Boolean,
	val productCode: String,
	val productName: String,
	val unitId: UUID,
)

data class ProductTotalKey(val warehouseId: UUID? = null, val productId: UUID? = null) : Serializable

// A row of stock_product_totals (V11): a material's stock in a warehouse.
@Entity
@Immutable
@Table(name = "stock_product_totals")
@IdClass(ProductTotalKey::class)
class ProductTotalRow(
	@Id val warehouseId: UUID,
	@Id val productId: UUID,
	val quantity: BigDecimal,
	val locations: Long,
	val warehouseCode: String,
	val productCode: String,
	val productName: String,
	val unitId: UUID,
)

interface BalanceRowRepository : JpaRepository<BalanceRow, BalanceKey>, JpaSpecificationExecutor<BalanceRow>

interface ProductTotalRowRepository : JpaRepository<ProductTotalRow, ProductTotalKey>, JpaSpecificationExecutor<ProductTotalRow>

data class UnitRef(val code: String, val label: String?, val decimalPlaces: Int?)

data class BalanceView(
	val warehouseId: UUID,
	val warehouseCode: String,
	val locationId: UUID,
	val locationCode: String,
	val aisle: String?,
	val rack: String?,
	val temporary: Boolean,
	val productId: UUID,
	val productCode: String,
	val productName: String,
	val unit: UnitRef?,
	val quantity: BigDecimal,
	val updatedAt: Instant,
)

data class ProductTotalView(
	val warehouseId: UUID,
	val warehouseCode: String,
	val productId: UUID,
	val productCode: String,
	val productName: String,
	val unit: UnitRef?,
	val quantity: BigDecimal,
	val locations: Long,
)

// Stock as the screens read it: by location (each balance), and by material in each warehouse. With AREA, only the
// reader's warehouses, and the totals count only those rows.
@Service
class StockQueries(
	private val balances: BalanceRowRepository,
	private val totals: ProductTotalRowRepository,
	private val catalogs: CatalogService,
) {
	// The material filter matches its code or its name.
	private fun <T> product(): TableFilter<T> = { root, builder, value ->
		val pattern = "%" + value.toString().trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
		builder.or(
			builder.like(builder.lower(root.get("productCode")), pattern, '\\'),
			builder.like(builder.lower(root.get("productName")), pattern, '\\'),
		)
	}

	private val balanceTable = TableSpec<BalanceRow>(
		filters = mapOf(
			"warehouseId" to TableSpec.id("warehouseId"),
			"locationId" to TableSpec.id("locationId"),
			"productId" to TableSpec.id("productId"),
			"location" to TableSpec.contains("locationCode"),
			"aisle" to TableSpec.code("aisle"),
			"rack" to TableSpec.code("rack"),
			"product" to product(),
		),
		sorts = mapOf("warehouse" to "warehouseCode", "location" to "locationCode", "product" to "productCode", "quantity" to "quantity"),
		defaultSort = Sort.by("warehouseCode", "locationCode", "productCode"),
	)

	private val totalTable = TableSpec<ProductTotalRow>(
		filters = mapOf(
			"warehouseId" to TableSpec.id("warehouseId"),
			"productId" to TableSpec.id("productId"),
			"product" to product(),
		),
		sorts = mapOf("warehouse" to "warehouseCode", "product" to "productCode", "quantity" to "quantity", "locations" to "locations"),
		defaultSort = Sort.by("productCode", "warehouseCode"),
	)

	@Transactional(readOnly = true)
	fun balances(user: CurrentUser, request: TableRequest, language: String): TablePage<BalanceView> =
		balances.table(request, balanceTable, scope(user)) { it }.mapRows { rows ->
			val units = units(rows.map { it.unitId }, language)
			rows.map {
				BalanceView(
					it.warehouseId, it.warehouseCode, it.locationId, it.locationCode, it.aisle, it.rack, it.temporary,
					it.productId, it.productCode, it.productName, units[it.unitId], it.quantity, it.updatedAt,
				)
			}
		}

	@Transactional(readOnly = true)
	fun productTotals(user: CurrentUser, request: TableRequest, language: String): TablePage<ProductTotalView> =
		totals.table(request, totalTable, scope(user)) { it }.mapRows { rows ->
			val units = units(rows.map { it.unitId }, language)
			rows.map { ProductTotalView(it.warehouseId, it.warehouseCode, it.productId, it.productCode, it.productName, units[it.unitId], it.quantity, it.locations) }
		}

	private fun units(ids: List<UUID>, language: String): Map<UUID, UnitRef> =
		catalogs.itemViews(ids, language).mapValues { (_, item) -> UnitRef(item.code, item.label, (item.attributes["decimal_places"] as? Number)?.toInt()) }

	// With AREA, the rows of the reader's warehouses only.
	private fun <T : Any> scope(user: CurrentUser): Specification<T>? {
		if (user.scopeOf(StockService.VIEW) == Scope.ALL) return null
		return Specification<T> { root, _, builder ->
			if (user.warehouseIds.isEmpty()) builder.disjunction() else root.get<UUID>("warehouseId").`in`(user.warehouseIds)
		}
	}
}
