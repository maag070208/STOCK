package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.security.Actor
import com.axzydev.stock.api.modules.stock.MovementCommand
import com.axzydev.stock.api.modules.stock.StockService
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.TestClock
import com.axzydev.stock.api.support.TestUser
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// F5-13 · e2e: an open order raises one alert a day for the people who must act on it — the day before its due date, on
// the due date and every day after it — and the same day is never raised twice. Run against real PostgreSQL.
class OrderAlertsTest : ApiTest() {

	@Autowired
	lateinit var stock: StockService

	@Autowired
	lateinit var clock: TestClock

	private val actor = Actor(null, "test")

	private fun addStock(location: String, product: String, quantity: Int) {
		stock.apply(
			actor,
			listOf(MovementCommand(StockService.RECEIPT, UUID.fromString(location), UUID.fromString(product), BigDecimal(quantity), "prueba", "test", UUID.randomUUID().toString())),
		)
	}

	// Today, as the API reads it for the due date rules (order-fulfillment.md section 3).
	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(OrderService.BUSINESS_ZONE))

	// The alerts of a person, read on the channel their roles allow: the warehouse roles work on the app, the office on the web.
	private fun alerts(user: TestUser, channel: String): List<JsonNode> =
		api.call("GET", "/orders/alerts", user.token, channel = channel).also { assertEquals(200, it.status, it.body) }.json.get("alerts").list

	private fun kinds(user: TestUser, number: String, channel: String): List<String> =
		alerts(user, channel).filter { it.get("order").get("number").asString() == number }.map { it.get("kind").asString() }.sorted()

	private fun numbers(user: TestUser, channel: String): List<String> =
		alerts(user, channel).map { it.get("order").get("number").asString() }

	// A person signs in again after the clock moved: their old token expired with the jump (testing.md rule 3).
	private fun again(user: TestUser, channel: String): TestUser {
		val login = api.login(user.username, user.password, channel)
		assertEquals(200, login.status, login.body)
		return TestUser(user.id, user.username, user.password, login.text("token"), login.text("refreshToken"))
	}

	private fun manager(f: OrdersFixture): TestUser =
		api.newUser("WAREHOUSE_MANAGER").also { api.put("/users/${it.id}/warehouses", f.admin, mapOf("warehouseIds" to listOf(f.warehouse))) }

	private fun create(f: OrdersFixture, product: String, quantity: Int, creator: TestUser): JsonNode =
		f.create(f.body(f.line(product, quantity), changes = mapOf("dueDate" to today().plusDays(1).toString())), token = creator.token).json

	private fun status(f: OrdersFixture, order: JsonNode): String =
		api.get("/orders/${order.get("id").asString()}", f.admin).json.get("status").get("code").asString()

	@Test
	fun `an order tells its operator and its managers from the day before, and its creator from the due date on`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val managerUser = manager(f)
		val creator = api.newUser("OFFICE")
		val order = create(f, product, 10, creator)
		val number = order.get("number").asString()
		api.put("/orders/${order.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))

		// The day before: the assigned operator and the manager who validates orders in that warehouse, one each. The
		// creator is not told yet, and a second look the same day raises nothing again.
		assertEquals(listOf("BEFORE"), kinds(f.operators[0], number, "app"))
		assertEquals(listOf("BEFORE"), kinds(managerUser, number, "web"))
		assertEquals(emptyList<String>(), numbers(creator, "web"))
		assertEquals(listOf("BEFORE"), kinds(f.operators[0], number, "app"), "the same day is never raised twice")

		// On the due date: the same people and the creator, one more each.
		clock.after(Duration.ofDays(1)) {
			val operator = again(f.operators[0], "app")
			assertEquals(listOf("BEFORE", "DUE"), kinds(operator, number, "app"))
			assertEquals(listOf("BEFORE", "DUE"), kinds(again(managerUser, "web"), number, "web"))
			assertEquals(listOf("DUE"), kinds(again(creator, "web"), number, "web"))
		}

		// Every day after the due date, while the order stays open.
		clock.after(Duration.ofDays(3)) {
			val operator = again(f.operators[0], "app")
			assertEquals(listOf("BEFORE", "DUE", "OVERDUE"), kinds(operator, number, "app"))
			assertEquals(listOf("BEFORE", "DUE", "OVERDUE"), kinds(again(managerUser, "web"), number, "web"))
			assertEquals(listOf("DUE", "OVERDUE"), kinds(again(creator, "web"), number, "web"))
			// The alert carries the order as the client shows it.
			val alert = alerts(operator, "app").first { it.get("order").get("number").asString() == number }
			assertEquals("OVERDUE", alert.get("kind").asString())
			assertEquals(f.warehouse, alert.get("order").get("warehouse").get("id").asString())
			assertEquals("CREATED", alert.get("order").get("status").get("code").asString())
		}
	}

	@Test
	fun `a validated or cancelled order raises no alert at all`() {
		val f = OrdersFixture(api)
		val product = f.product()
		addStock(f.locations[0], product, 100)
		val creator = api.newUser("OFFICE")
		val cancelled = create(f, product, 10, creator)
		val validated = create(f, product, 10, creator)
		val line = validated.get("lines").get(0)
		api.put("/orders/${validated.get("id").asString()}/assignment", f.admin, mapOf("operatorId" to f.operators[0].id))
		api.call(
			"POST", "/orders/${validated.get("id").asString()}/picks", f.operators[0].token,
			mapOf("lineId" to line.get("id").asString(), "locationId" to f.locations[0], "quantity" to 10), channel = "app",
		)
		assertEquals("FULFILLED", status(f, validated))
		assertEquals(200, api.post("/orders/${validated.get("id").asString()}/validation", f.admin, emptyMap<String, Any>()).status)
		assertEquals("VALIDATED", status(f, validated))
		assertEquals(200, api.post("/orders/${cancelled.get("id").asString()}/cancellation", f.admin).status)

		// The day before and the days after: neither order raises anything, for anyone.
		assertEquals(emptyList<String>(), numbers(f.operators[0], "app"))
		assertEquals(emptyList<String>(), numbers(creator, "web"))
		clock.after(Duration.ofDays(4)) {
			assertEquals(emptyList<String>(), numbers(again(f.operators[0], "app"), "app"))
			assertEquals(emptyList<String>(), numbers(again(creator, "web"), "web"))
		}
	}
}
