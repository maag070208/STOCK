package com.axzydev.stock.api.modules.catalogs

import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// F1-15 and F1-16 · e2e: value catalogs are data, administered through the API.
class CatalogsEndpointTest : ApiTest() {

	private fun code(prefix: String) = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

	private fun codes(path: String, token: String, language: String = "es") =
		api.call("GET", path, token, headers = mapOf("Accept-Language" to language)).json.list.map { it.get("code").asString() }

	@Test
	fun `the address catalogs are seeded with Mexico, its 32 states with their country, and payment terms without values`() {
		val admin = api.adminToken()
		assertEquals(listOf("MX"), codes("/catalogs/COUNTRY/items", admin))
		val states = api.get("/catalogs/STATE/items", admin).json.list
		assertEquals(32, states.size)
		assertTrue(states.all { it.get("attributes").get("country").asString() == "MX" })
		assertEquals("Ciudad de México", states.first { it.get("code").asString() == "MX_CMX" }.get("label").asString())
		// Other tests add their own, marked term; the seed adds none.
		assertEquals(emptyList<String>(), codes("/catalogs/PAYMENT_TERMS/items?all=true", admin) - com.axzydev.stock.api.support.Api.TEST_TERMS)
	}

	@Test
	fun `the seed catalogs are there, each value with its Spanish and English label`() {
		val admin = api.adminToken()
		val catalogs = api.get("/catalogs", admin).json.list.associate { it.get("code").asString() to it }
		for (seed in listOf("INVOICE_STATUS", "ORDER_STATUS", "UNIT_OF_MEASURE")) assertTrue(catalogs.getValue(seed).get("isSystem").asBoolean(), seed)

		assertEquals(listOf("OPEN", "PENDING", "IN_ASSIGNMENT", "CLOSED"), codes("/catalogs/INVOICE_STATUS/items", admin))
		assertEquals(listOf("CREATED", "FULFILLING", "FULFILLED", "VALIDATED", "CANCELLED"), codes("/catalogs/ORDER_STATUS/items", admin))
		assertEquals(listOf("PIECE"), codes("/catalogs/UNIT_OF_MEASURE/items", admin))

		val spanish = api.get("/catalogs/INVOICE_STATUS/items", admin).json.list
		assertEquals(listOf("Abierta", "Pendiente", "En asignación", "Cerrada"), spanish.map { it.get("label").asString() })
		val english = api.call("GET", "/catalogs/INVOICE_STATUS/items", admin, headers = mapOf("Accept-Language" to "en")).json.list
		assertEquals(listOf("Open", "Pending", "In assignment", "Closed"), english.map { it.get("label").asString() })
		assertEquals("Abierta", english[0].get("labels").get("es").get("label").asString())

		// Behavior reads attributes, not names.
		assertTrue(spanish.last().get("attributes").get("terminal").asBoolean())
		assertEquals(0, api.get("/catalogs/UNIT_OF_MEASURE/items", admin).json.get(0).get("attributes").get("decimal_places").asInt())
	}

	@Test
	fun `an item is created, labeled and deactivated, and a deactivated item stays in history`() {
		val admin = api.adminToken()
		val catalog = code("TEST")
		assertEquals(201, api.post("/catalogs", admin, mapOf("code" to catalog.lowercase())).status)
		assertEquals("DUPLICATE_VALUE", api.post("/catalogs", admin, mapOf("code" to catalog)).code)

		val labels = mapOf("es" to mapOf("label" to "Kilogramo"), "en" to mapOf("label" to "Kilogram", "description" to "Weight"))
		val created = api.post("/catalogs/$catalog/items", admin, mapOf("code" to "kg", "labels" to labels, "attributes" to mapOf("decimal_places" to 3)))
		assertEquals(201, created.status)
		assertEquals("KG", created.text("code"))
		assertEquals("Kilogramo", created.text("label"))
		assertEquals("DUPLICATE_VALUE", api.post("/catalogs/$catalog/items", admin, mapOf("code" to "KG", "labels" to labels)).code)
		api.post("/catalogs/$catalog/items", admin, mapOf("code" to "LITER", "labels" to mapOf("es" to mapOf("label" to "Litro"), "en" to mapOf("label" to "Liter"))))
		assertEquals(listOf("KG", "LITER"), codes("/catalogs/$catalog/items", admin))

		// A new label and a new order.
		val relabeled = api.patch("/catalogs/$catalog/items/KG", admin, mapOf("labels" to mapOf("es" to mapOf("label" to "Kilo")), "sortOrder" to 5))
		assertEquals(200, relabeled.status)
		assertEquals("Kilo", relabeled.text("label"))
		assertEquals("Kilogram", relabeled.json.get("labels").get("en").get("label").asString())
		assertEquals(listOf("LITER", "KG"), codes("/catalogs/$catalog/items", admin))

		// Deactivated: not offered for new records, still there for history.
		assertFalse(api.patch("/catalogs/$catalog/items/KG", admin, mapOf("active" to false)).json.get("active").asBoolean())
		assertEquals(listOf("LITER"), codes("/catalogs/$catalog/items", admin))
		assertEquals(listOf("LITER", "KG"), codes("/catalogs/$catalog/items?all=true", admin))

		val audit = api.post("/audit-log/query", admin, mapOf("filters" to mapOf("targetId" to "$catalog:KG"), "sort" to mapOf("key" to "occurredAt", "direction" to "asc")))
		assertEquals(listOf("CATALOG_ITEM_CREATED", "CATALOG_ITEM_UPDATED", "CATALOG_ITEM_UPDATED"), audit.json.get("data").list.map { it.get("action").asString() })
		assertEquals("Kilogramo", audit.json.get("data").get(1).get("before").get("labels").get("es").asString())
	}

	@Test
	fun `a code cannot change, an active item needs both labels, and a field outside the spec is rejected`() {
		val admin = api.adminToken()
		val catalog = code("TEST")
		api.post("/catalogs", admin, mapOf("code" to catalog))
		val both = mapOf("es" to mapOf("label" to "Caja"), "en" to mapOf("label" to "Box"))
		api.post("/catalogs/$catalog/items", admin, mapOf("code" to "BOX", "labels" to both))

		val recode = api.patch("/catalogs/$catalog/items/BOX", admin, mapOf("code" to "CRATE"))
		assertEquals(400, recode.status)
		assertEquals("UNKNOWN_FIELD", recode.code)
		assertEquals("code", recode.json.get("details").get(0).get("field").asString())
		assertEquals(listOf("BOX"), codes("/catalogs/$catalog/items", admin))

		val oneLabel = api.post("/catalogs/$catalog/items", admin, mapOf("code" to "BAG", "labels" to mapOf("es" to mapOf("label" to "Bolsa"))))
		assertEquals("FIELD_REQUIRED", oneLabel.code)
		assertEquals("labels.en", oneLabel.json.get("details").get(0).get("field").asString())
		// Inactive, it can be saved with one label. It cannot be activated until the other arrives.
		assertEquals(201, api.post("/catalogs/$catalog/items", admin, mapOf("code" to "BAG", "labels" to mapOf("es" to mapOf("label" to "Bolsa")), "active" to false)).status)
		assertEquals("FIELD_REQUIRED", api.patch("/catalogs/$catalog/items/BAG", admin, mapOf("active" to true)).code)
		assertEquals(200, api.patch("/catalogs/$catalog/items/BAG", admin, mapOf("active" to true, "labels" to mapOf("en" to mapOf("label" to "Bag")))).status)

		val unknownLabelField = api.patch("/catalogs/$catalog/items/BOX", admin, mapOf("labels" to mapOf("es" to mapOf("label" to "Caja", "color" to "red"))))
		assertEquals(400, unknownLabelField.status)
		assertEquals("UNKNOWN_FIELD", unknownLabelField.code)
		assertEquals("UNKNOWN_FIELD", api.patch("/catalogs/$catalog/items/BOX", admin, mapOf("attributes" to mapOf("color" to "red"))).code)
		assertEquals(404, api.patch("/catalogs/$catalog/items/NOPE", admin, mapOf("active" to false)).status)
		assertEquals(404, api.get("/catalogs/NO_SUCH_CATALOG/items", admin).status)
	}

	@Test
	fun `reading needs catalogs view and changing needs catalogs manage`() {
		val office = api.newUser("OFFICE")
		assertEquals(200, api.get("/catalogs", office.token).status)
		assertEquals(200, api.get("/catalogs/INVOICE_STATUS/items", office.token).status)
		val refused = api.post("/catalogs", office.token, mapOf("code" to code("TEST")))
		assertEquals(403, refused.status)
		assertEquals("INSUFFICIENT_PERMISSIONS", refused.code)
		assertEquals(403, api.patch("/catalogs/INVOICE_STATUS/items/OPEN", office.token, mapOf("active" to false)).status)

		val appUser = api.newUser("APP_USER", channel = "app")
		assertEquals(403, api.get("/catalogs", appUser.token, channel = "app").status)
	}
}
