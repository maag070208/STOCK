package com.axzydev.stock.api.modules.entries

import com.axzydev.stock.api.support.Api
import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.BusinessDays
import com.axzydev.stock.api.support.ApiResponse
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import java.util.UUID

// What a material entry needs, created through the API as a person would: a supplier, a warehouse with a rack,
// materials, and operators of that warehouse in its staff template.
class EntriesFixture(private val api: Api) {
	val admin: String = api.adminToken()
	val supplier: String = api.post("/suppliers", admin, api.partner()).text("id")
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

	fun number(): String = uniqueCode("FAC")

	// A line of the invoice body: a material, its quantity, and the operators' shares, if any.
	fun line(product: String, quantity: Int, vararg shares: Pair<TestUser, Int>): Map<String, Any?> =
		mapOf("productId" to product, "quantity" to quantity) +
			(if (shares.isEmpty()) emptyMap() else mapOf("assignments" to shares.map { (operator, share) -> mapOf("operatorId" to operator.id, "quantity" to share) }))

	fun body(vararg lines: Map<String, Any?>, number: String = number(), changes: Map<String, Any?> = emptyMap()): Map<String, Any?> =
		mapOf("number" to number, "supplierId" to supplier, "invoiceDate" to BusinessDays.today().toString(), "warehouseId" to warehouse, "lines" to lines.toList()) + changes

	fun create(body: Map<String, Any?>, token: String = admin, key: String = UUID.randomUUID().toString()): ApiResponse =
		api.call("POST", "/invoices", token, body, headers = mapOf("Idempotency-Key" to key))
}
