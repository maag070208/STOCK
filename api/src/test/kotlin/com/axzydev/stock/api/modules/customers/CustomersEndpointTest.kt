package com.axzydev.stock.api.modules.customers

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// F2-10 · e2e: customers mirror suppliers, with the same validators. A person's RFC has 13 characters, a company's 12.
class CustomersEndpointTest : ApiTest() {

	@Test
	fun `a customer with a 13 character RFC is created, and so is one with a 12 character RFC`() {
		val person = api.post("/customers", api.adminToken(), api.partner(changes = mapOf("name" to "Juana López", "taxId" to "LOPJ800101AB1")))
		assertEquals(201, person.status, person.body)
		assertEquals("LOPJ800101AB1", person.text("taxId"))
		val company = api.post("/customers", api.adminToken(), api.partner(changes = mapOf("taxId" to "ADN010203AB1")))
		assertEquals(201, company.status, company.body)
	}

	@Test
	fun `the supplier rules apply to customers and their contacts`() {
		val wrong = api.post("/customers", api.adminToken(), api.partner(changes = mapOf("taxId" to "LOPJ801301AB1")))
		assertEquals("TAX_ID_INVALID", wrong.code)
		val id = api.post("/customers", api.adminToken(), api.partner()).text("id")
		assertEquals("CONTACT_CHANNEL_REQUIRED", api.post("/customers/$id/contacts", api.adminToken(), mapOf("name" to "Luis")).code)
		assertEquals("PHONE_INVALID", api.post("/customers/$id/contacts", api.adminToken(), mapOf("name" to "Luis", "phone" to "55-1234")).code)
		val contact = api.post("/customers/$id/contacts", api.adminToken(), mapOf("name" to "Luis", "email" to "luis@cliente.mx"))
		assertEquals(201, contact.status, contact.body)
		val contactId = contact.text("id")
		assertEquals(false, api.delete("/customers/$id/contacts/$contactId", api.adminToken()).json.get("active").asBoolean())
		assertEquals(false, api.get("/customers/$id", api.adminToken()).json.get("contacts").list.single().get("active").asBoolean())
	}

	@Test
	fun `office reads customers and only customers manage changes them`() {
		val id = api.post("/customers", api.adminToken(), api.partner()).text("id")
		val office = api.newUser("OFFICE")
		assertEquals(200, api.get("/customers/$id", office.token).status)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.patch("/customers/$id", office.token, mapOf("name" to "Otro")).code)
	}
}
