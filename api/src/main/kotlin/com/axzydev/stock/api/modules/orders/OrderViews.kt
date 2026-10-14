package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.customers.CustomerService
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.stock.CodeLabel
import com.axzydev.stock.api.modules.stock.UnitRef
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Ref(val id: UUID, val code: String?, val name: String?)

data class OrderLineView(
	val id: UUID,
	val product: Ref,
	val unit: UnitRef?,
	val quantity: BigDecimal,
	val pickedQuantity: BigDecimal,
)

data class OrderView(
	val id: UUID,
	val number: String,
	val warehouse: Ref,
	val customer: Ref,
	val contact: Ref?,
	val dueDate: LocalDate,
	val status: CodeLabel,
	val operator: Ref?,
	val createdBy: Ref?,
	val createdAt: Instant,
	val validatedAt: Instant?,
	val validatedBy: Ref?,
	val lines: List<OrderLineView>? = null,
)

// Builds what the API answers about orders. Every name, code and label of a page is looked up together.
@Component
class OrderViews(
	private val lines: OrderLineRepository,
	private val customers: CustomerService,
	private val warehouses: WarehouseService,
	private val products: ProductService,
	private val people: UserService,
	private val catalogs: CatalogService,
) {
	fun headers(orders: List<Order>, language: String): List<OrderView> {
		val customerNames = customers.names(orders.map { it.customerId }, orders.mapNotNull { it.contactId })
		val warehouseCodes = warehouses.codes(orders.map { it.warehouseId })
		val statuses = catalogs.itemViews(orders.map { it.statusId }, language)
		val peopleById = people.people(orders.mapNotNull { it.operatorId } + orders.mapNotNull { it.createdBy } + orders.mapNotNull { it.validatedBy })
		return orders.map { order ->
			OrderView(
				id = order.id!!,
				number = order.number,
				warehouse = Ref(order.warehouseId, warehouseCodes[order.warehouseId], null),
				customer = Ref(order.customerId, customerNames.first[order.customerId]?.first, customerNames.first[order.customerId]?.second),
				contact = order.contactId?.let { Ref(it, null, customerNames.second[it]) },
				dueDate = order.dueDate,
				status = statuses[order.statusId].let { CodeLabel(it?.code ?: "", it?.label) },
				operator = order.operatorId?.let { person -> peopleById[person]?.let { Ref(person, it.username, it.name) } },
				createdBy = order.createdBy?.let { person -> peopleById[person]?.let { Ref(person, it.username, it.name) } },
				createdAt = order.createdAt,
				validatedAt = order.validatedAt,
				validatedBy = order.validatedBy?.let { person -> peopleById[person]?.let { Ref(person, it.username, it.name) } },
				lines = null,
			)
		}
	}

	fun full(order: Order, language: String): OrderView =
		headers(listOf(order), language).single().copy(lines = orderLines(order.id!!, language))

	private fun orderLines(orderId: UUID, language: String): List<OrderLineView> {
		val list = lines.findByOrderId(orderId)
		val found = products.stockProducts(list.map { it.productId })
		val units = catalogs.itemViews(list.map { it.unitId }, language)
		return list.sortedBy { found[it.productId]?.code }.map { line ->
			val product = found[line.productId]
			val unit = units[line.unitId]
			OrderLineView(
				id = line.id!!,
				product = Ref(line.productId, product?.code, product?.name),
				unit = unit?.let { UnitRef(it.code, it.label, (it.attributes["decimal_places"] as? Number)?.toInt()) },
				quantity = line.quantity,
				pickedQuantity = line.pickedQuantity,
			)
		}
	}
}
