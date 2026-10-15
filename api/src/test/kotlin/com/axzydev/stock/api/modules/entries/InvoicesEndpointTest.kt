package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.BusinessDays
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.util.UUID

// F4-07 to F4-13 · e2e: the invoice, its lines, its idempotent creation, its visibility by warehouse, the staff
// templates, and the two paths to the same assignments.
class InvoicesEndpointTest : ApiTest() {

	private fun fields(body: JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	private fun shares(invoice: JsonNode) = invoice.get("lines").list.flatMap { line ->
		line.get("assignments").list.map { line.get("product").get("id").asString() to (it.get("operator").get("id").asString() to it.get("expectedQuantity").asInt()) }
	}.toSet()

	@Test
	fun `an invoice is created OPEN with its lines, and each line takes its material's unit`() {
		val f = EntriesFixture(api)
		val created = f.create(f.body(f.line(f.product(), 100)))
		assertEquals(201, created.status, created.body)
		assertEquals("OPEN", created.json.get("status").get("code").asString())
		assertEquals("Abierta", created.json.get("status").get("label").asString())
		val line = created.json.get("lines").get(0)
		assertEquals("PIECE", line.get("unit").get("code").asString())
		assertEquals(100, line.get("quantity").asInt())
		assertEquals(created.text("id"), api.get("/invoices/${created.text("id")}", f.admin).text("id"))
	}

	@Test
	fun `the invoice number is unique per supplier, and the same number from another supplier is accepted`() {
		val f = EntriesFixture(api)
		val number = f.number()
		assertEquals(201, f.create(f.body(number = number)).status)
		assertEquals(setOf("number" to "DUPLICATE_VALUE"), fields(f.create(f.body(number = number)).json))
		val other = api.post("/suppliers", f.admin, api.partner()).text("id")
		assertEquals(201, f.create(f.body(number = number, changes = mapOf("supplierId" to other))).status)
	}

	@Test
	fun `every field of the invoice and its lines is validated`() {
		val f = EntriesFixture(api)
		val product = f.product()
		val inactive = api.post("/suppliers", f.admin, api.partner()).text("id")
		api.delete("/suppliers/$inactive", f.admin)
		val wrong = f.create(
			f.body(
				f.line(product, 0), f.line(product, 5),
				changes = mapOf("number" to "FAC 1", "supplierId" to inactive, "invoiceDate" to BusinessDays.today(1).toString()),
			),
		)
		assertEquals(
			setOf(
				"number" to "FORMAT_INVALID", "supplierId" to "CATALOG_VALUE_INVALID", "invoiceDate" to "DATE_IN_FUTURE",
				"lines.0.quantity" to "QUANTITY_INVALID", "lines.1.productId" to "DUPLICATE_VALUE",
			),
			fields(wrong.json),
		)
		assertEquals(setOf("lines.0.quantity" to "QUANTITY_INVALID"), fields(f.create(f.body(mapOf("productId" to f.product(), "quantity" to 1.5))).json))
		val empty = api.call("POST", "/invoices", f.admin, mapOf<String, Any>(), headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
		assertEquals(setOf("number", "supplierId", "invoiceDate", "warehouseId"), fields(empty.json).map { it.first }.toSet())
	}

	@Test
	fun `a line is added on the detail, a material appears once, and a closed or counted line cannot change`() {
		val f = EntriesFixture(api)
		val id = f.create(f.body()).text("id")
		val product = f.product()
		val added = api.post("/invoices/$id/lines", f.admin, mapOf("productId" to product, "quantity" to 20))
		assertEquals(201, added.status, added.body)
		assertEquals("PIECE", added.json.get("unit").get("code").asString())
		assertEquals(setOf("productId" to "DUPLICATE_VALUE"), fields(api.post("/invoices/$id/lines", f.admin, mapOf("productId" to product, "quantity" to 5)).json))
		assertEquals(30, api.patch("/invoices/$id/lines/${added.text("id")}", f.admin, mapOf("quantity" to 30)).json.get("quantity").asInt())
		assertEquals("UNKNOWN_FIELD", api.patch("/invoices/$id/lines/${added.text("id")}", f.admin, mapOf("unit" to "KG")).code)
	}

	@Test
	fun `creating an invoice twice with the same key creates one, and another request with that key is refused`() {
		val f = EntriesFixture(api)
		val key = UUID.randomUUID().toString()
		val body = f.body(f.line(f.product(), 10))
		val first = f.create(body, key = key)
		val second = f.create(body, key = key)
		assertEquals(201, second.status, second.body)
		assertEquals(first.text("id"), second.text("id"))
		assertEquals(1, api.post("/invoices/query", f.admin, mapOf("filters" to mapOf("number" to body["number"]))).json.get("total").asInt())
		assertEquals("IDEMPOTENCY_KEY_REUSED", f.create(f.body(), key = key).code)
		assertEquals("FIELD_REQUIRED", api.post("/invoices", f.admin, f.body()).code)
	}

	@Test
	fun `a manager sees the invoices of their warehouses, office sees every one, and the rest are not found`() {
		val f = EntriesFixture(api)
		val id = f.create(f.body()).text("id")
		val stranger = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${stranger.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(api.createWarehouse())))
		assertEquals("NOT_FOUND", api.get("/invoices/$id", stranger.token).code)
		assertEquals(0, api.post("/invoices/query", stranger.token, mapOf("filters" to mapOf("warehouseId" to f.warehouse))).json.get("total").asInt())
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse)))
		assertEquals(200, api.get("/invoices/$id", manager.token).status)
		assertEquals(200, api.get("/invoices/$id", api.newUser("OFFICE").token).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", f.create(f.body(), token = manager.token).code)
	}

	@Test
	fun `the manager of a warehouse edits its staff template, which lists only its active operators`() {
		val f = EntriesFixture(api)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse)))
		val newcomer = f.operator()
		val updated = api.put("/warehouses/${f.warehouse}/staff", manager.token, mapOf("userIds" to f.operators.map { it.id } + newcomer.id))
		assertEquals(200, updated.status, updated.body)
		assertEquals(3, updated.json.list.size)
		// Someone who is not an operator of this warehouse cannot be in its template.
		val office = api.newUser("OFFICE")
		assertEquals(setOf("userIds" to "CATALOG_VALUE_INVALID"), fields(api.put("/warehouses/${f.warehouse}/staff", manager.token, mapOf("userIds" to listOf(office.id))).json))
		// A deactivated operator leaves the template's list.
		api.delete("/users/${newcomer.id}", f.admin)
		assertEquals(2, api.get("/warehouses/${f.warehouse}/staff", manager.token).json.list.size)
		val other = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${other.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(api.createWarehouse())))
		assertEquals("INSUFFICIENT_PERMISSIONS", api.put("/warehouses/${f.warehouse}/staff", other.token, mapOf("userIds" to emptyList<String>())).code)
	}

	@Test
	fun `assigning after saving and assigning before saving store the same assignments`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val screws = f.product()
		// Path A: save, then assign.
		val pathA = f.create(f.body(f.line(screws, 100)))
		assertEquals("OPEN", pathA.json.get("status").get("code").asString())
		val line = pathA.json.get("lines").get(0).get("id").asString()
		val assigned = api.put("/invoices/${pathA.text("id")}/lines/$line/assignments", f.admin, mapOf("assignments" to listOf(mapOf("operatorId" to a.id, "quantity" to 60), mapOf("operatorId" to b.id, "quantity" to 40))))
		assertEquals(200, assigned.status, assigned.body)
		val afterA = api.get("/invoices/${pathA.text("id")}", f.admin).json
		assertEquals("PENDING", afterA.get("status").get("code").asString())
		// Path B: assign and save once.
		val pathB = f.create(f.body(f.line(screws, 100, a to 60, b to 40))).json
		assertEquals("PENDING", pathB.get("status").get("code").asString())
		assertEquals(shares(afterA), shares(pathB))
	}

	@Test
	fun `the shares of a line must add up to it, and each operator must be in the warehouse's template`() {
		val f = EntriesFixture(api)
		val (a, b) = f.operators
		val refused = f.create(f.body(f.line(f.product(), 100, a to 60, b to 30)))
		assertEquals(setOf("lines.0.assignments" to "ASSIGNMENT_TOTAL_MISMATCH"), fields(refused.json))
		val invoice = f.create(f.body(f.line(f.product(), 100))).json
		val path = "/invoices/${invoice.get("id").asString()}/lines/${invoice.get("lines").get(0).get("id").asString()}/assignments"
		assertEquals("ASSIGNMENT_TOTAL_MISMATCH", api.put(path, f.admin, mapOf("assignments" to listOf(mapOf("operatorId" to a.id, "quantity" to 60)))).code)
		val outsider = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		assertEquals(
			setOf("assignments.0.operatorId" to "CATALOG_VALUE_INVALID"),
			fields(api.put(path, f.admin, mapOf("assignments" to listOf(mapOf("operatorId" to outsider.id, "quantity" to 100)))).json),
		)
		assertTrue(api.put(path, f.admin, mapOf("assignments" to listOf(mapOf("operatorId" to a.id, "quantity" to 100)))).status == 200)
	}

	@Test
	fun `who creates invoices picks among the active warehouses, without reading locations`() {
		val f = EntriesFixture(api)
		val office = api.newUser("OFFICE")
		val options = api.get("/invoices/warehouse-options", office.token)
		assertEquals(200, options.status, options.body)
		assertTrue(options.json.list.any { it.get("id").asString() == f.warehouse })
		val inactive = api.createWarehouse()
		api.delete("/warehouses/$inactive", f.admin)
		assertTrue(api.get("/invoices/warehouse-options", office.token).json.list.none { it.get("id").asString() == inactive })
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/invoices/warehouse-options", api.newUser("WAREHOUSE_MANAGER").token).code)
	}

	@Test
	fun `the manager sees who can join the template, the active operators of the warehouse`() {
		val f = EntriesFixture(api)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse)))
		val newcomer = f.operator()
		val candidates = api.get("/warehouses/${f.warehouse}/staff/candidates", manager.token).json.list.map { it.get("id").asString() }.toSet()
		assertTrue(candidates.containsAll(f.operators.map { it.id } + newcomer.id), candidates.toString())
		assertTrue(manager.id !in candidates)
	}
}

