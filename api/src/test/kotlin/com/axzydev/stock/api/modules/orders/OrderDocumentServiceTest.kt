package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.modules.catalogs.CatalogItemView
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.customers.CustomerService
import com.axzydev.stock.api.modules.identity.users.Person
import com.axzydev.stock.api.modules.identity.users.UserService
import com.axzydev.stock.api.modules.products.ProductService
import com.axzydev.stock.api.modules.products.StockProduct
import com.axzydev.stock.api.modules.warehouses.WarehouseService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// F5-14 · unit: the document data carries every field of section 10 of the order spec, looked up from what the order
// stores: the warehouse, the order and its due date, the customer and its contact, each line, the creator, the
// operators, the validator and the signature lines.
class OrderDocumentServiceTest {

	private val orderId = UUID.randomUUID()
	private val warehouseId = UUID.randomUUID()
	private val customerId = UUID.randomUUID()
	private val contactId = UUID.randomUUID()
	private val productId = UUID.randomUUID()
	private val unitId = UUID.randomUUID()
	private val createdBy = UUID.randomUUID()
	private val firstOperator = UUID.randomUUID()
	private val secondOperator = UUID.randomUUID()
	private val validator = UUID.randomUUID()

	private val lines = mockk<OrderLineRepository>()
	private val picks = mockk<OrderPickRepository>()
	private val products = mockk<ProductService>()
	private val catalogs = mockk<CatalogService>()
	private val customers = mockk<CustomerService>()
	private val warehouses = mockk<WarehouseService>()
	private val people = mockk<UserService>()

	private val service = OrderDocumentService(
		mockk(relaxed = true), mockk(relaxed = true), lines, picks, customers, warehouses, products, catalogs, people,
		mockk(relaxed = true), Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), java.time.ZoneOffset.UTC),
	)

	private val order = Order(
		number = "ORD-1", warehouseId = warehouseId, customerId = customerId, contactId = contactId,
		dueDate = LocalDate.of(2026, 10, 20), statusId = UUID.randomUUID(), operatorId = firstOperator,
		createdBy = createdBy, createdAt = Instant.parse("2026-10-13T10:00:00Z"), updatedAt = Instant.parse("2026-10-14T10:00:00Z"),
	).apply {
		id = orderId
		validatedBy = validator
	}

	private fun person(id: UUID, name: String) = Person(id, "user$name", name, true, mockk(relaxed = true))

	@Test
	fun `the document carries every field of section 10`() {
		val line = OrderLine(orderId, productId, unitId, BigDecimal(10)).apply { id = UUID.randomUUID() }
		every { lines.findByOrderId(orderId) } returns listOf(line)
		every { products.stockProducts(listOf(productId)) } returns mapOf(productId to StockProduct(productId, "M1", "Codo galvanizado", unitId, true, 0))
		every { catalogs.itemViews(listOf(unitId), "es") } returns mapOf(
			unitId to CatalogItemView("PIECE", "Pieza", null, 1, true, emptyMap(), emptyMap()),
		)
		every { customers.names(listOf(customerId), listOf(contactId)) } returns (mapOf(customerId to ("CLI1" to "Aceros SA")) to mapOf(contactId to "Ana"))
		every { picks.findByLineIdIn(listOf(line.id!!)) } returns listOf(
			OrderPick(line.id!!, UUID.randomUUID(), BigDecimal(4), UUID.randomUUID(), firstOperator, Instant.now()).apply { id = UUID.randomUUID() },
			OrderPick(line.id!!, UUID.randomUUID(), BigDecimal(6), UUID.randomUUID(), secondOperator, Instant.now()).apply { id = UUID.randomUUID() },
		)
		every {
			people.people(listOf(createdBy, firstOperator, validator, secondOperator))
		} returns mapOf(
			createdBy to person(createdBy, "Oficina"),
			firstOperator to person(firstOperator, "Ana"),
			secondOperator to person(secondOperator, "Beto"),
			validator to person(validator, "Gerente"),
		)
		every { warehouses.codes(listOf(warehouseId)) } returns mapOf(warehouseId to "CENTRAL")

		val data = service.data(order, "es")
		assertEquals("CENTRAL", data.warehouse)
		assertEquals("ORD-1", data.number)
		assertEquals("2026-10-20", data.dueDate)
		assertEquals("Aceros SA", data.customer)
		assertEquals("Ana", data.contact)
		assertEquals(listOf(OrderDocumentLine("M1", "Codo galvanizado", "Pieza", "10")), data.lines)
		assertEquals("Oficina", data.createdBy)
		// Everyone who picked something is named, in the order the picks happened.
		assertEquals(listOf("Ana", "Beto"), data.operators)
		assertEquals("Gerente", data.validatedBy)
		assertEquals("Comprobante de orden", data.labels.title)
		assertEquals(listOf("Entrega · Nombre y firma", "Recibe · Nombre y firma"), OrderDocumentService.signatureLabels(data.labels))
	}

	@Test
	fun `an order nobody picked yet names its assigned operator, and the document reads in English too`() {
		val line = OrderLine(orderId, productId, unitId, BigDecimal("2.50")).apply { id = UUID.randomUUID() }
		every { lines.findByOrderId(orderId) } returns listOf(line)
		every { products.stockProducts(listOf(productId)) } returns mapOf(productId to StockProduct(productId, "M1", "Codo galvanizado", unitId, true, 2))
		every { catalogs.itemViews(listOf(unitId), "en") } returns mapOf(
			unitId to CatalogItemView("PIECE", "Piece", null, 1, true, emptyMap(), emptyMap()),
		)
		every { customers.names(listOf(customerId), listOf(contactId)) } returns (mapOf(customerId to ("CLI1" to "Aceros SA")) to mapOf(contactId to "Ana"))
		every { picks.findByLineIdIn(listOf(line.id!!)) } returns emptyList()
		every { people.people(listOf(createdBy, firstOperator, validator)) } returns mapOf(
			createdBy to person(createdBy, "Oficina"),
			firstOperator to person(firstOperator, "Ana"),
			validator to person(validator, "Gerente"),
		)
		every { warehouses.codes(listOf(warehouseId)) } returns mapOf(warehouseId to "CENTRAL")

		val data = service.data(order, "en")
		assertEquals(listOf("Ana"), data.operators)
		assertEquals("2.5", data.lines.single().quantity)
		assertEquals("Order receipt", data.labels.title)
		assertEquals("Due date", data.labels.dueDate)
	}
}
