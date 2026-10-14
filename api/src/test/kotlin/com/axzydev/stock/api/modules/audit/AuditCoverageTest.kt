package com.axzydev.stock.api.modules.audit

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.orders.OrdersFixture
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiResponse
import com.axzydev.stock.api.support.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

// F1 acceptance criterion 11 · e2e: every write, through the API, leaves its record in the audit log,
// and so does every denial. A module that adds writes adds them here.
class AuditCoverageTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val written = mutableListOf<Triple<String, String, String>>()

	// Runs one write, checks it succeeded, and remembers which record it must leave.
	private fun write(action: String, targetType: String, targetId: String, call: () -> ApiResponse) {
		val response = call()
		assertTrue(response.status in 200..299, "$action: ${response.status} ${response.body}")
		written += Triple(action, targetType, targetId)
	}

	private fun random() = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

	@Test
	fun `every write is in the audit log`() {
		val admin = api.adminToken()

		// Users.
		val created = api.createUser("OFFICE")
		val id = created.json.get("user").get("id").asString()
		val username = created.json.get("user").get("username").asString()
		written += Triple("USER_CREATED", "user", id)
		write("USER_UPDATED", "user", id) { api.patch("/users/$id", admin, mapOf("name" to "Persona cambiada")) }
		write("USER_ROLES_CHANGED", "user", id) { api.put("/users/$id/roles", admin, mapOf("roles" to listOf("OFFICE", "APP_USER"))) }
		write("USER_WAREHOUSES_CHANGED", "user", id) { api.put("/users/$id/warehouses", admin, mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		write("PERMISSION_EXCEPTION_SET", "user", id) { api.put("/users/$id/permissions/audit.view", admin, mapOf("scope" to "ALL", "reason" to "Revisión")) }
		write("PERMISSION_EXCEPTION_REMOVED", "user", id) { api.delete("/users/$id/permissions/audit.view", admin) }

		// The person's own session: sign-in, password change, language, sign-out.
		val temporary = created.text("temporaryPassword")
		val first = api.login(username, temporary)
		written += Triple("LOGIN", "user", id)
		val changed = api.post("/auth/change-password", first.text("token"), mapOf("currentPassword" to temporary, "newPassword" to "Password-Propia-01"))
		assertEquals(200, changed.status, changed.body)
		written += Triple("PASSWORD_CHANGED", "user", id)
		write("USER_UPDATED", "user", id) { api.patch("/auth/me", changed.text("token"), mapOf("language" to "en")) }
		write("LOGOUT", "user", id) { api.post("/auth/logout", changed.text("token"), mapOf("refreshToken" to changed.text("refreshToken"))) }

		// Failed sign-ins lock the account; an administrator unlocks it.
		repeat(5) { assertEquals(true, api.login(username, "Clave-incorrecta-1").status in 400..499) }
		written += Triple("LOGIN_FAILED", "user", id)
		written += Triple("USER_LOCKED", "user", id)
		write("USER_UNLOCKED", "user", id) { api.post("/users/$id/unlock", admin) }
		write("USER_PASSWORD_RESET", "user", id) { api.post("/users/$id/reset-password", admin) }
		write("USER_DEACTIVATED", "user", id) { api.delete("/users/$id", admin) }
		write("USER_REACTIVATED", "user", id) { api.post("/users/$id/reactivate", admin) }

		// Roles and the matrix.
		val role = "T_" + random()
		write("ROLE_CREATED", "role", role) { api.post("/roles", admin, mapOf("key" to role, "name" to "Rol de prueba")) }
		write("ROLE_UPDATED", "role", role) { api.patch("/roles/$role", admin, mapOf("name" to "Rol cambiado")) }
		write("PERMISSION_MATRIX_CHANGED", "role", role) {
			api.put("/roles/matrix", admin, mapOf("changes" to listOf(mapOf("roleKey" to role, "permissionKey" to "catalogs.view", "scope" to "ALL"))))
		}
		write("ROLE_DELETED", "role", role) { api.delete("/roles/$role", admin) }

		// Policies. The condition matches nobody, so the policy never denies another test's request.
		val policy = "P_" + random()
		val body = mapOf(
			"key" to policy, "priority" to 900, "effect" to "DENY", "action" to "users.permissions.set", "active" to true,
			"conditions" to listOf(mapOf("field" to "target.id", "operator" to "eq", "value" to UUID.randomUUID().toString())), "roles" to listOf<String>(),
		)
		write("POLICY_CREATED", "policy", policy) { api.post("/policies", admin, body) }
		write("POLICY_UPDATED", "policy", policy) { api.put("/policies/$policy", admin, body - "key" + ("priority" to 901)) }
		write("POLICY_DELETED", "policy", policy) { api.delete("/policies/$policy", admin) }

		// Catalogs.
		val catalog = "T_" + random()
		val labels = mapOf("es" to mapOf("label" to "Caja"), "en" to mapOf("label" to "Box"))
		write("CATALOG_CREATED", "catalog", catalog) { api.post("/catalogs", admin, mapOf("code" to catalog)) }
		write("CATALOG_ITEM_CREATED", "catalog_item", "$catalog:BOX") { api.post("/catalogs/$catalog/items", admin, mapOf("code" to "BOX", "labels" to labels)) }
		write("CATALOG_ITEM_UPDATED", "catalog_item", "$catalog:BOX") { api.patch("/catalogs/$catalog/items/BOX", admin, mapOf("sortOrder" to 5)) }

		// Warehouses and locations.
		val warehouse = api.post("/warehouses", admin, mapOf("code" to com.axzydev.stock.api.support.Api.uniqueCode("W"), "name" to "Almacén")).text("id")
		written += Triple("WAREHOUSE_CREATED", "warehouse", warehouse)
		write("WAREHOUSE_UPDATED", "warehouse", warehouse) { api.patch("/warehouses/$warehouse", admin, mapOf("name" to "Almacén cambiado")) }
		val rack = api.post("/locations/racks", admin, mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05", "levels" to 1))
		assertEquals(201, rack.status, rack.body)
		written += Triple("RACK_CREATED", "warehouse", warehouse)
		val location = rack.json.get("locations").get(0).get("id").asString()
		write("LOCATION_DEACTIVATED", "location", location) { api.delete("/locations/$location", admin) }
		write("LOCATION_REACTIVATED", "location", location) { api.post("/locations/$location/reactivate", admin) }
		write("WAREHOUSE_DEACTIVATED", "warehouse", warehouse) { api.delete("/warehouses/$warehouse", admin) }
		write("WAREHOUSE_REACTIVATED", "warehouse", warehouse) { api.post("/warehouses/$warehouse/reactivate", admin) }

		// Materials.
		val product = api.post("/products", admin, mapOf("code" to com.axzydev.stock.api.support.Api.uniqueCode("P").replace("-", ""), "name" to "Codo", "unit" to "PIECE"))
		assertEquals(201, product.status, product.body)
		val productId = product.text("id")
		written += Triple("PRODUCT_CREATED", "product", productId)
		write("PRODUCT_UPDATED", "product", productId) { api.patch("/products/$productId", admin, mapOf("name" to "Codo 90°")) }
		write("PRODUCT_DEACTIVATED", "product", productId) { api.delete("/products/$productId", admin) }
		write("PRODUCT_REACTIVATED", "product", productId) { api.post("/products/$productId/reactivate", admin) }

		// Suppliers and customers, with a contact each.
		for (kind in listOf("supplier", "customer")) {
			val upper = kind.uppercase()
			val created = api.post("/${kind}s", admin, api.partner())
			assertEquals(201, created.status, created.body)
			val partnerId = created.text("id")
			written += Triple("${upper}_CREATED", kind, partnerId)
			write("${upper}_UPDATED", kind, partnerId) { api.patch("/${kind}s/$partnerId", admin, mapOf("name" to "Otro nombre")) }
			val contact = api.post("/${kind}s/$partnerId/contacts", admin, mapOf("name" to "Ana", "phone" to "3312345678"))
			assertEquals(201, contact.status, contact.body)
			val contactId = contact.text("id")
			written += Triple("${upper}_CONTACT_CREATED", "${kind}_contact", contactId)
			write("${upper}_CONTACT_UPDATED", "${kind}_contact", contactId) { api.patch("/${kind}s/$partnerId/contacts/$contactId", admin, mapOf("name" to "Ana María")) }
			write("${upper}_CONTACT_DEACTIVATED", "${kind}_contact", contactId) { api.delete("/${kind}s/$partnerId/contacts/$contactId", admin) }
			write("${upper}_CONTACT_REACTIVATED", "${kind}_contact", contactId) { api.post("/${kind}s/$partnerId/contacts/$contactId/reactivate", admin) }
			write("${upper}_DEACTIVATED", kind, partnerId) { api.delete("/${kind}s/$partnerId", admin) }
			write("${upper}_REACTIVATED", kind, partnerId) { api.post("/${kind}s/$partnerId/reactivate", admin) }
		}

		// Material entry and receiving, through one invoice.
		val f = com.axzydev.stock.api.modules.entries.EntriesFixture(api)
		written += Triple("STAFF_TEMPLATE_CHANGED", "warehouse", f.warehouse)
		val operator = f.operators[0]
		val invoice = f.create(f.body(f.line(f.product(), 10)))
		assertEquals(201, invoice.status, invoice.body)
		val invoiceId = invoice.text("id")
		written += Triple("INVOICE_CREATED", "invoice", invoiceId)
		val extra = api.post("/invoices/$invoiceId/lines", admin, mapOf("productId" to f.product(), "quantity" to 3))
		written += Triple("INVOICE_LINE_ADDED", "invoice", invoiceId)
		write("INVOICE_LINE_UPDATED", "invoice", invoiceId) { api.patch("/invoices/$invoiceId/lines/${extra.text("id")}", admin, mapOf("quantity" to 4)) }
		write("INVOICE_LINE_REMOVED", "invoice", invoiceId) { api.delete("/invoices/$invoiceId/lines/${extra.text("id")}", admin) }
		val lineId = invoice.json.get("lines").get(0).get("id").asString()
		val assigned = api.put("/invoices/$invoiceId/lines/$lineId/assignments", admin, mapOf("assignments" to listOf(mapOf("operatorId" to operator.id, "quantity" to 10))))
		assertEquals(200, assigned.status, assigned.body)
		written += Triple("RECEIPT_ASSIGNMENTS_CHANGED", "invoice", invoiceId)
		written += Triple("INVOICE_STATUS_CHANGED", "invoice", invoiceId)
		val assignment = assigned.json.get("assignments").get(0).get("id").asString()
		write("RECEIPT_COUNTED", "receipt_assignment", assignment) {
			api.call("POST", "/receipts/assignments/$assignment/count", operator.token, mapOf("countedQuantity" to 9), channel = "app")
		}
		write("DISCREPANCY_ADJUSTED", "receipt_assignment", assignment) { api.post("/receipts/assignments/$assignment/adjustment", admin, mapOf("note" to "Faltó uno")) }
		write("RECEIPT_PLACED", "receipt_assignment", assignment) {
			api.call("POST", "/receipts/assignments/$assignment/placements", operator.token, mapOf("placements" to listOf(mapOf("locationId" to f.locations[0], "quantity" to 9))), channel = "app")
		}

		// Orders, from creation to validation, with the three reassignment actions on the way.
		val o = OrdersFixture(api)
		written += Triple("STAFF_TEMPLATE_CHANGED", "warehouse", o.warehouse)
		val ordered = o.product()
		stock.apply(
			Actor(null, "test"),
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(o.locations[0]), UUID.fromString(ordered), BigDecimal(200), "prueba", "test", UUID.randomUUID().toString())),
		)
		val order = o.create(o.body(o.line(ordered, 30))).json
		val orderId = order.get("id").asString()
		val orderLine = order.get("lines").get(0).get("id").asString()
		written += Triple("ORDER_CREATED", "order", orderId)
		write("ORDER_ASSIGNED", "order", orderId) { api.put("/orders/$orderId/assignment", admin, mapOf("operatorId" to o.operators[0].id)) }
		write("ORDER_PICKED", "order", orderId) {
			api.call("POST", "/orders/$orderId/picks", o.operators[0].token, mapOf("lineId" to orderLine, "locationId" to o.locations[0], "quantity" to 25), channel = "app")
		}
		written += Triple("ORDER_STATUS_CHANGED", "order", orderId)
		write("ORDER_REASSIGNED", "order", orderId) { api.post("/orders/$orderId/reassignments/operator", admin, mapOf("operatorId" to o.operators[1].id, "note" to "Cambio de turno")) }
		val target = o.create(o.body(o.line(ordered, 20))).json
		write("ORDER_PICKS_TRANSFERRED", "order", orderId) {
			api.post("/orders/$orderId/reassignments/transfer", admin, mapOf("lineId" to orderLine, "targetOrderId" to target.get("id").asString(), "quantity" to 20, "note" to "Traspaso"))
		}
		write("ORDER_PICKS_RETURNED", "order", orderId) {
			api.post("/orders/$orderId/reassignments/return", admin, mapOf("lineId" to orderLine, "locationId" to o.locations[0], "quantity" to 5, "note" to "Devolución"))
		}
		write("ORDER_CANCELLED", "order", orderId) { api.post("/orders/$orderId/cancellation", admin) }

		// A shortage the customer accepts becomes a complete order, and then a validated one.
		val short = o.create(o.body(o.line(ordered, 10))).json
		val shortId = short.get("id").asString()
		val shortLine = short.get("lines").get(0).get("id").asString()
		write("ORDER_ASSIGNED", "order", shortId) { api.put("/orders/$shortId/assignment", admin, mapOf("operatorId" to o.operators[0].id)) }
		write("ORDER_PICKED", "order", shortId) {
			api.call("POST", "/orders/$shortId/picks", o.operators[0].token, mapOf("lineId" to shortLine, "locationId" to o.locations[0], "quantity" to 8), channel = "app")
		}
		write("ORDER_DIFFERENCE_ACCEPTED", "order", shortId) { api.post("/orders/$shortId/accept-difference", admin, mapOf("note" to "El cliente acepta 8 de 10")) }
		write("ORDER_VALIDATED", "order", shortId) { api.post("/orders/$shortId/validation", admin, emptyMap<String, Any>()) }

		val missing = written.distinct().filter { (action, targetType, targetId) ->
			val found = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("action" to action, "targetType" to targetType, "targetId" to targetId)))
			assertEquals(200, found.status, found.body)
			found.json.get("total").asInt() == 0
		}
		assertEquals(emptyList<Triple<String, String, String>>(), missing, "writes without their audit record")
	}

	@Test
	fun `a denial is in the audit log with the permission it lacked`() {
		val office = api.newUser("OFFICE")
		assertEquals(403, api.post("/roles", office.token, mapOf("key" to "T_" + random(), "name" to "Rol")).status)
		val log = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "ACCESS_DENIED", "actor" to office.username)))
		assertEquals(1, log.json.get("total").asInt(), log.body)
		assertTrue(log.body.contains("roles.manage"), log.body)
	}
}
