package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiDownload
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestUser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.UUID

// F5-14 · e2e: validating an order stores its comprobante, a download answers the same bytes every time, and the
// document does not change when the customer does. Run against real PostgreSQL.
class OrderDocumentTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	private val actor = Actor(null, "test")

	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	private fun pick(operator: TestUser, order: JsonNode, line: JsonNode, location: String, quantity: Int) =
		api.call(
			"POST", "/orders/${order.get("id").asString()}/picks", operator.token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to location, "quantity" to quantity), channel = "app",
		)

	// A download on the channel the reader's roles allow: the warehouse roles work on the app.
	private fun document(token: String, order: JsonNode, channel: String = "web"): ApiDownload =
		api.download("/orders/${order.get("id").asString()}/document", token, channel)

	// The text a reader would read on the page.
	private fun text(download: ApiDownload): String = Loader.loadPDF(download.bytes).use { PDFTextStripper().getText(it) }

	// An order of 10 that is picked, and validated by the administrator.
	private fun validated(f: OrdersFixture, product: String): Pair<JsonNode, JsonNode> {
		val contact = api.post("/customers/${f.customer}/contacts", f.admin, mapOf("name" to "Ana Ruiz", "phone" to "3312345678")).text("id")
		val order = f.create(f.body(f.line(product, 10), changes = mapOf("contactId" to contact))).json
		val line = order.get("lines").get(0)
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))
		pick(f.operators[0], order, line, f.locations[0], 10)
		val validated = api.post("/orders/${order.get("id").asString()}/validation", f.admin, emptyMap<String, Any>())
		assertEquals(200, validated.status, validated.body)
		return order to api.get("/orders/${order.get("id").asString()}", f.admin).json
	}

	@Test
	fun `validating an order stores its comprobante, and a second download answers the same bytes`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val (order, view) = validated(f, product)

		val download = document(f.admin, order)
		assertEquals(200, download.status, download.text)
		assertEquals("application/pdf", download.contentType)
		assertTrue(download.contentDisposition.orEmpty().contains("comprobante-${view.get("number").asString()}.pdf"), download.contentDisposition)
		assertTrue(download.bytes.size > 0 && String(download.bytes, 0, 4, Charsets.US_ASCII) == "%PDF")

		// Every field of section 10 is on the page.
		val page = text(download)
		assertTrue(page.contains("Comprobante de orden"), page)
		assertTrue(page.contains(view.get("warehouse").get("code").asString()), page)
		assertTrue(page.contains(view.get("number").asString()), page)
		assertTrue(page.contains(view.get("dueDate").asString()), page)
		assertTrue(page.contains("Aceros del Norte"), page)
		assertTrue(page.contains("Ana Ruiz"), page)
		assertTrue(page.contains("Tornillo"), page)
		assertTrue(page.contains("Pieza"), page)
		assertTrue(page.contains("10"), page)
		assertTrue(page.contains(view.get("createdBy").get("name").asString()), page)
		assertTrue(page.contains(view.get("operator").get("name").asString()), page)
		assertTrue(page.contains(view.get("validatedBy").get("name").asString()), page)
		assertTrue(page.contains("Entrega · Nombre y firma"), page)
		assertTrue(page.contains("Recibe · Nombre y firma"), page)

		// A second download is the same file, and so is the one after the customer changed its name.
		val again = document(f.admin, order)
		assertEquals(200, again.status)
		assertTrue(download.bytes.contentEquals(again.bytes), "the document is answered byte for byte")
		assertEquals(200, api.patch("/customers/${f.customer}", f.admin, mapOf("name" to "Aceros del Sur")).status)
		val renamed = document(f.admin, order)
		assertTrue(download.bytes.contentEquals(renamed.bytes), "the stored document does not change with the customer")
		assertTrue(text(renamed).contains("Aceros del Norte"), "the stored document keeps the name it was built with")
	}

	@Test
	fun `a document is only for the readers of its warehouse, and only once the order is validated`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 10)
		val order = f.create(f.body(f.line(product, 10))).json
		val line = order.get("lines").get(0)
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))
		pick(f.operators[0], order, line, f.locations[0], 10)

		// Not validated yet: there is nothing to download.
		assertEquals(404, document(f.admin, order).status)
		assertEquals(200, api.post("/orders/${order.get("id").asString()}/validation", f.admin, emptyMap<String, Any>()).status)

		// Someone who reads no order of this warehouse, and someone without orders.view.
		val stranger = f.operator().also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(api.createWarehouse()))) }
		assertEquals(404, document(stranger.token, order, "app").status)
		val appUser = api.newUser("APP_USER", channel = "app")
		val refused = api.download("/orders/${order.get("id").asString()}/document", appUser.token, channel = "app")
		assertEquals(403, refused.status)
		assertFalse(refused.text.contains("Comprobante"))
	}
}
