package com.axzydev.stock.api.modules.suppliers

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

// F2-09 · e2e: suppliers and their contacts. Every field is validated; a contact needs an email or a phone.
class SuppliersEndpointTest : ApiTest() {

	private fun fields(body: JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	private fun create(changes: Map<String, Any?> = emptyMap()) = api.post("/suppliers", api.adminToken(), api.partner(changes = changes))

	private fun address(changes: Map<String, Any?>) = (api.partner()["address"] as Map<*, *>) + changes

	@Test
	fun `an administrator creates a supplier with its address and payment terms, and adds a contact`() {
		val created = create(mapOf("taxId" to " adn010203ab1 "))
		assertEquals(201, created.status, created.body)
		assertEquals("ADN010203AB1", created.text("taxId"))
		assertEquals("Jalisco", created.json.get("address").get("state").get("label").asString())
		assertEquals("MX", created.json.get("address").get("country").get("code").asString())
		assertEquals("Plazo de prueba", created.json.get("paymentTerms").get("label").asString())
		val id = created.text("id")

		val contact = api.post("/suppliers/$id/contacts", api.adminToken(), mapOf("name" to "Ana Pérez", "email" to " Ana@Aceros.com.mx ", "phone" to "+52 (33) 1234-5678"))
		assertEquals(201, contact.status, contact.body)
		assertEquals("ana@aceros.com.mx", contact.text("email"))
		assertEquals("3312345678", contact.text("phone"))
		assertEquals(listOf("Ana Pérez"), api.get("/suppliers/$id", api.adminToken()).json.get("contacts").list.map { it.get("name").asString() })
	}

	@Test
	fun `a wrong RFC, email or phone is refused with its own code`() {
		assertEquals(setOf("taxId" to "TAX_ID_INVALID"), fields(create(mapOf("taxId" to "ADN011303AB1")).json))
		val id = create().text("id")
		val contacts = "/suppliers/$id/contacts"
		assertEquals(setOf("email" to "EMAIL_INVALID"), fields(api.post(contacts, api.adminToken(), mapOf("name" to "Ana", "email" to "ana.aceros.com")).json))
		assertEquals(setOf("email" to "EMAIL_INVALID"), fields(api.post(contacts, api.adminToken(), mapOf("name" to "Ana", "email" to "ana@aceros")).json))
		assertEquals(setOf("phone" to "PHONE_INVALID"), fields(api.post(contacts, api.adminToken(), mapOf("name" to "Ana", "phone" to "12345678")).json))
	}

	@Test
	fun `a contact with neither email nor phone is refused, and so is a change that leaves it without both`() {
		val id = create().text("id")
		val refused = api.post("/suppliers/$id/contacts", api.adminToken(), mapOf("name" to "Ana"))
		assertEquals(400, refused.status)
		assertEquals(setOf("email" to "CONTACT_CHANNEL_REQUIRED", "phone" to "CONTACT_CHANNEL_REQUIRED"), fields(refused.json))
		val contact = api.post("/suppliers/$id/contacts", api.adminToken(), mapOf("name" to "Ana", "phone" to "3312345678")).text("id")
		assertEquals("CONTACT_CHANNEL_REQUIRED", api.patch("/suppliers/$id/contacts/$contact", api.adminToken(), mapOf("phone" to "")).code)
		val changed = api.patch("/suppliers/$id/contacts/$contact", api.adminToken(), mapOf("email" to "ana@aceros.mx", "phone" to ""))
		assertEquals(200, changed.status, changed.body)
		assertTrue(changed.json.get("phone").isNull)
	}

	@Test
	fun `every part of the address is validated, and the state must be of the country`() {
		val empty = api.post("/suppliers", api.adminToken(), mapOf<String, Any>())
		assertEquals(setOf("code", "name", "taxId", "address", "paymentTerms"), fields(empty.json).map { it.first }.toSet())
		val wrong = create(
			mapOf(
				"address" to address(mapOf("street" to " ", "exteriorNumber" to "12 B", "postalCode" to "4410", "state" to "JALISCO", "country" to "US")),
				"paymentTerms" to "NEVER",
			),
		)
		assertEquals(
			setOf(
				"address.street" to "FIELD_REQUIRED", "address.exteriorNumber" to "FORMAT_INVALID", "address.postalCode" to "POSTAL_CODE_INVALID",
				"address.country" to "CATALOG_VALUE_INVALID", "address.state" to "CATALOG_VALUE_INVALID", "paymentTerms" to "CATALOG_VALUE_INVALID",
			),
			fields(wrong.json),
		)
		assertEquals(201, create(mapOf("address" to address(mapOf("exteriorNumber" to "S/N", "interiorNumber" to null)))).status)
	}

	@Test
	fun `a supplier changes everything but its code, and a deactivated one stays readable`() {
		val admin = api.adminToken()
		val id = create().text("id")
		val changed = api.patch("/suppliers/$id", admin, mapOf("name" to "Aceros del Sur", "address" to address(mapOf("state" to "MX_NLE", "city" to "Monterrey"))))
		assertEquals(200, changed.status, changed.body)
		assertEquals("Aceros del Sur", changed.text("name"))
		assertEquals("Nuevo León", changed.json.get("address").get("state").get("label").asString())
		assertEquals("UNKNOWN_FIELD", api.patch("/suppliers/$id", admin, mapOf("code" to "OTHER")).code)
		assertEquals("DUPLICATE_VALUE", api.post("/suppliers", admin, api.partner(code = changed.text("code"))).code)

		assertFalse(api.delete("/suppliers/$id", admin).json.get("active").asBoolean())
		assertEquals(200, api.get("/suppliers/$id", admin).status)
		val inactive = api.post("/suppliers/query", admin, mapOf("filters" to mapOf("code" to changed.text("code"), "active" to false))).json
		assertEquals(1, inactive.get("total").asInt())
		assertNull(inactive.get("data").get(0).get("contacts").takeIf { !it.isNull })
		val log = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetType" to "supplier", "targetId" to id)))
		assertEquals(setOf("SUPPLIER_CREATED", "SUPPLIER_UPDATED", "SUPPLIER_DEACTIVATED"), log.json.get("data").list.map { it.get("action").asString() }.toSet())
	}

	@Test
	fun `office reads suppliers and cannot change them, and the warehouse operator cannot read them`() {
		val id = create().text("id")
		val office = api.newUser("OFFICE")
		assertEquals(200, api.get("/suppliers/$id", office.token).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/suppliers", office.token, api.partner()).code)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/suppliers/$id/contacts", office.token, mapOf("name" to "Ana", "phone" to "3312345678")).code)
		val operator = api.newUser("WAREHOUSE_OPERATOR", channel = "app")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.call("GET", "/suppliers/$id", operator.token, channel = "app").code)
	}
}
