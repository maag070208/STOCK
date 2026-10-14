package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.support.Api
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiResponse
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import java.time.LocalDate
import java.util.UUID

// What an order needs, created through the API as a person would: a customer, a warehouse with a rack, materials and
// the operators of that warehouse in its staff template.
class OrdersFixture(private val api: Api) {
	val admin: String = api.adminToken()
	val customer: String = api.post("/customers", admin, api.partner()).text("id")
	val warehouse: String = api.createWarehouse()
	val locations: List<String> = api.post("/locations/racks", admin, mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05", "levels" to 2))
		.json.get("locations").list.map { it.get("id").asString() }
	val operators: List<TestUser> = List(2) { operator() }

	init {
		check(api.put("/warehouses/$warehouse/staff", admin, mapOf("userIds" to operators.map { it.id })).status == 200)
	}

	// An operator of this warehouse, signed in on the app.
	fun operator(): TestUser = api.newUser("WAREHOUSE_OPERATOR", channel = "app").also {
		api.put("/users/${it.id}/warehouses", admin, mapOf("warehouseIds" to listOf(warehouse)))
	}

	fun product(name: String = "Tornillo"): String =
		api.post("/products", admin, mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	fun number(): String = uniqueCode("ORD")

	fun line(product: String, quantity: Int): Map<String, Any?> = mapOf("productId" to product, "quantity" to quantity)

	fun body(vararg lines: Map<String, Any?>, number: String = number(), changes: Map<String, Any?> = emptyMap()): Map<String, Any?> =
		mapOf(
			"number" to number,
			"warehouseId" to warehouse,
			"customerId" to customer,
			"dueDate" to LocalDate.now().plusDays(3).toString(),
			"lines" to lines.toList(),
		) + changes

	fun create(body: Map<String, Any?>, token: String = admin, key: String = UUID.randomUUID().toString()): ApiResponse =
		api.call("POST", "/orders", token, body, headers = mapOf("Idempotency-Key" to key))
}
