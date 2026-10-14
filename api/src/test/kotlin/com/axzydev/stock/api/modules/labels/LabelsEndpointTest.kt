package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.support.Api.Companion.uniqueCode
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import tools.jackson.databind.JsonNode
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// F3-01 to F3-10 · e2e: label templates with their versions, printers, printing to a fake printer, and the print audit.
// The fake printer is a TCP server on the printing port the test profile sets, and records what it receives
// (testing.md rule 7).
class LabelsEndpointTest : ApiTest() {

	@Value("\${stockflow.printing.port}")
	var printingPort: Int = 0

	private fun fields(body: JsonNode) = body.get("details").list.map { it.get("field").asString() to it.get("code").asString() }.toSet()

	// A new key has no placeholders of its own yet, only {{copies}}. The product label's ZPL uses the product's.
	private val zpl = "^XA^FO30,30^FDEtiqueta^FS^FO30,150^BY3^BCN,110,Y,N,N^FD12345678^FS^PQ{{copies}}^XZ"
	private val productZpl = "^XA^FO30,30^FD{{name}}^FS^FO30,150^BY3^BCN,110,Y,N,N^FD{{barcode}}^FS^PQ{{copies}}^XZ"

	private fun key() = "T_" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

	private fun template(key: String = key(), body: String = zpl) =
		api.post("/label-templates", api.adminToken(), mapOf("key" to key, "nameEs" to "Etiqueta de prueba", "nameEn" to "Test label", "zpl" to body))

	// Listens once on the printing port, and gives what it received.
	private fun fakePrinter(): Future<String> {
		val server = ServerSocket(printingPort)
		return Executors.newSingleThreadExecutor().submit<String> {
			server.use { it.accept().use { socket -> socket.getInputStream().readAllBytes().decodeToString() } }
		}
	}

	private fun printer(warehouse: String = api.createWarehouse(), ip: String = "127.0.0.1") =
		api.post("/printers", api.adminToken(), mapOf("name" to "Zebra de prueba", "warehouseId" to warehouse, "ipAddress" to ip)).also { assertEquals(201, it.status, it.body) }.text("id")

	private fun product(name: String = "Tornillo") =
		api.post("/products", api.adminToken(), mapOf("code" to uniqueCode("P").replace("-", ""), "name" to name, "unit" to "PIECE")).text("id")

	@Test
	fun `the seed has the product and location labels, named in the request's language`() {
		val list = api.get("/label-templates", api.adminToken()).json.list
		val product = list.single { it.get("key").asString() == "PRODUCT" }
		assertEquals("Etiqueta de producto", product.get("name").asString())
		assertTrue(list.any { it.get("key").asString() == "LOCATION" })
		val english = api.call("GET", "/label-templates", api.adminToken(), headers = mapOf("Accept-Language" to "en")).json.list
		assertEquals("Product label", english.single { it.get("key").asString() == "PRODUCT" }.get("name").asString())
	}

	@Test
	fun `a template without XZ, with a forbidden command or an unknown placeholder is refused, and no version is created`() {
		assertEquals(setOf("zpl" to "ZPL_BLOCK_INVALID"), fields(template(body = zpl.removeSuffix("^XZ")).json))
		assertEquals(setOf("zpl" to "ZPL_COMMAND_NOT_ALLOWED"), fields(template(body = zpl.replace("^PQ", "~JR^PQ")).json))
		assertEquals(setOf("zpl" to "ZPL_PLACEHOLDER_UNKNOWN"), fields(template(body = zpl.replace("Etiqueta", "{{name}}")).json))
		val id = api.get("/label-templates", api.adminToken()).json.list.single { it.get("key").asString() == "PRODUCT" }.get("id").asString()
		val before = api.get("/label-templates/$id", api.adminToken()).json.get("version").asInt()
		val refused = api.patch("/label-templates/$id", api.adminToken(), mapOf("zpl" to productZpl.replace("{{barcode}}", "{{rack}}")))
		assertEquals(setOf("zpl" to "ZPL_PLACEHOLDER_UNKNOWN"), fields(refused.json))
		assertEquals("La plantilla usa un campo que no existe para este tipo de etiqueta.", refused.json.get("details").get(0).get("message").asString())
		assertEquals(before, api.get("/label-templates/$id", api.adminToken()).json.get("version").asInt())
	}

	@Test
	fun `the validation report names each problem as the editor types`() {
		val report = api.post("/label-templates/validate", api.adminToken(), mapOf("key" to "PRODUCT", "zpl" to productZpl.replace("^PQ", "^HH^PQ").replace("{{name}}", "{{rack}}")))
		assertEquals(200, report.status, report.body)
		assertEquals(setOf("ZPL_COMMAND_NOT_ALLOWED" to "^HH", "ZPL_PLACEHOLDER_UNKNOWN" to "rack"), report.json.get("problems").list.map { it.get("code").asString() to it.get("token").asString() }.toSet())
	}

	@Test
	fun `each change of the ZPL is a new version, the old ones stay readable, and the key never changes`() {
		val created = template()
		assertEquals(201, created.status, created.body)
		assertEquals(1, created.json.get("version").asInt())
		val id = created.text("id")
		assertEquals(2, api.patch("/label-templates/$id", api.adminToken(), mapOf("zpl" to zpl.replace("30,30", "40,40"))).json.get("version").asInt())
		assertEquals(3, api.patch("/label-templates/$id", api.adminToken(), mapOf("zpl" to zpl.replace("30,30", "50,50"))).json.get("version").asInt())
		val versions = api.get("/label-templates/$id/versions", api.adminToken()).json.list
		assertEquals(listOf(3, 2, 1), versions.map { it.get("version").asInt() })
		assertTrue(versions.last().get("zpl").asString().contains("30,30"))
		// The print after the second save uses version 3.
		val received = fakePrinter()
		val printed = api.post("/labels/print", api.adminToken(), mapOf("templateKey" to created.text("key"), "printerId" to printer(), "copies" to 1))
		assertEquals(3, printed.json.get("version").asInt(), printed.body)
		assertTrue(received.get(10, TimeUnit.SECONDS).contains("^FO50,50"))
		assertEquals("UNKNOWN_FIELD", api.patch("/label-templates/$id", api.adminToken(), mapOf("key" to "OTRA")).code)
		assertFalse(api.patch("/label-templates/$id", api.adminToken(), mapOf("active" to false)).json.get("active").asBoolean())
	}

	@Test
	fun `printers have a valid address and a warehouse, and only admins see or change them`() {
		val wrong = api.post("/printers", api.adminToken(), mapOf("name" to "Z", "warehouseId" to api.createWarehouse(), "ipAddress" to "192.168.1"))
		assertEquals(setOf("name" to "FORMAT_INVALID", "ipAddress" to "IP_ADDRESS_INVALID"), fields(wrong.json))
		assertEquals("UNKNOWN_FIELD", api.post("/printers", api.adminToken(), mapOf("name" to "Zebra", "warehouseId" to api.createWarehouse(), "ipAddress" to "10.0.0.5", "port" to 80)).code)
		val id = printer()
		assertEquals(9100, api.get("/printers/$id", api.adminToken()).json.get("port").asInt())
		val office = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.get("/printers/$id", office.token).code)
		assertEquals("INSUFFICIENT_PERMISSIONS", api.post("/label-templates", office.token, mapOf("key" to key())).code)
	}

	@Test
	fun `a print fills the active version, sends three copies to the printer, cuts long values, and is audited once`() {
		val printerId = printer()
		// A product name cannot carry ^, ~ or \ (products.md), so the sanitizer's removal is covered by LabelRulesTest;
		// here a name longer than 60 characters reaches the label cut to 60.
		val name = "TUBO 1-7/8\" X 6 MT " + "A".repeat(70)
		val productId = product(name)
		val received = fakePrinter()
		val printed = api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "PRODUCT", "productId" to productId, "printerId" to printerId, "copies" to 3))
		assertEquals(200, printed.status, printed.body)
		val zplSent = received.get(10, TimeUnit.SECONDS)
		assertTrue(zplSent.contains("^PQ3"), zplSent)
		assertTrue(zplSent.contains("^FD" + name.take(60) + "^FS"), zplSent)
		val log = api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "LABEL_PRINTED", "targetId" to productId))).json
		assertEquals(1, log.get("total").asInt())
		val entry = log.get("data").get(0).get("after")
		assertEquals(3, entry.get("copies").asInt())
		assertEquals(printerId, entry.get("printerId").asString())
		assertTrue(entry.get("version").asInt() >= 1)
	}

	@Test
	fun `a printer that does not answer is PRINTER_UNREACHABLE, and nothing is recorded`() {
		val productId = product()
		// Nothing listens on the printing port: the connection is refused or times out.
		val failed = api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "PRODUCT", "productId" to productId, "printerId" to printer(), "copies" to 1))
		assertEquals(503, failed.status)
		assertEquals("PRINTER_UNREACHABLE", failed.code)
		assertEquals(0, api.post("/audit-log/query", api.adminToken(), mapOf("filters" to mapOf("action" to "LABEL_PRINTED", "targetId" to productId))).json.get("total").asInt())
	}

	@Test
	fun `a print names a printer of the catalog, never an address, with 1 to 100 copies`() {
		val productId = product()
		assertEquals(setOf("printerId" to "CATALOG_VALUE_INVALID"), fields(api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "PRODUCT", "productId" to productId, "printerId" to UUID.randomUUID().toString(), "copies" to 1)).json))
		assertEquals("UNKNOWN_FIELD", api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "PRODUCT", "productId" to productId, "ipAddress" to "10.0.0.9", "copies" to 1)).code)
		assertEquals(setOf("copies" to "FORMAT_INVALID"), fields(api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "PRODUCT", "productId" to productId, "printerId" to printer(), "copies" to 101)).json))
	}

	@Test
	fun `a location prints on a printer of its own warehouse, and an app user cannot print`() {
		val warehouse = api.createWarehouse()
		val location = api.post("/locations/racks", api.adminToken(), mapOf("warehouseId" to warehouse, "aisle" to "K", "rack" to "05", "levels" to 1)).json.get("locations").get(0).get("id").asString()
		val elsewhere = printer()
		assertEquals("LOCATION_NOT_IN_WAREHOUSE", api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "LOCATION", "locationId" to location, "printerId" to elsewhere, "copies" to 1)).code)
		val received = fakePrinter()
		assertEquals(200, api.post("/labels/print", api.adminToken(), mapOf("templateKey" to "LOCATION", "locationId" to location, "printerId" to printer(warehouse), "copies" to 1)).status)
		assertTrue(received.get(10, TimeUnit.SECONDS).contains("K05A"))
		val appUser = api.newUser("APP_USER", channel = "app")
		assertEquals("INSUFFICIENT_PERMISSIONS", api.call("POST", "/labels/print", appUser.token, mapOf("templateKey" to "PRODUCT"), channel = "app").code)
	}

	@Test
	fun `whoever prints sees the printers of their warehouses, without their addresses`() {
		val warehouse = api.createWarehouse()
		val mine = printer(warehouse)
		val manager = api.newUser("WAREHOUSE_MANAGER")
		api.put("/users/${manager.id}/warehouses", api.adminToken(), mapOf("warehouseIds" to listOf(warehouse)))
		val listed = api.get("/labels/printers", manager.token).json.list
		assertEquals(listOf(mine), listed.map { it.get("id").asString() })
		assertFalse(listed.single().has("ipAddress"))
	}
}
