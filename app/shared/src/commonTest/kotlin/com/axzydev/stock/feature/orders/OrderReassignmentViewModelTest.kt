package com.axzydev.stock.feature.orders

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.orders.OrdersApi
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// F5-19 · unit: the reassignment view model opens the order of an alert, finds the picked line of the scanned material,
// and lists only the open orders of the same warehouse whose line for that material still needs some of it.
@OptIn(ExperimentalCoroutinesApi::class)
class OrderReassignmentViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	// The alert of an order due tomorrow, with a picked line of 20 out of 30.
	private val alerts = """
		{"alerts":[{"id":"al-1","kind":"BEFORE","raisedOn":"2026-10-14",
		"order":{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-15",
		"status":{"code":"FULFILLING","label":"Surtiendo"},"operator":{"id":"u1","code":"admin","name":"Admin"},"lines":null}}]}
	""".trimIndent()

	private val source = """
		{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-15",
		"status":{"code":"FULFILLING","label":"Surtiendo"},"operator":{"id":"u1","code":"admin","name":"Admin"},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"30.000000","pickedQuantity":"20.000000"}]}
	""".trimIndent()

	// The query answers every open order of the warehouse, including the source and one of another warehouse: the view
	// model keeps only another order of the same warehouse that needs the material.
	private val candidates = """
		{"data":[{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-15",
		"status":{"code":"FULFILLING","label":"Surtiendo"},"lines":null},
		{"id":"o2","number":"ORD-2","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-16",
		"status":{"code":"CREATED","label":"Creada"},"lines":null},
		{"id":"o3","number":"ORD-3","warehouse":{"id":"w2","code":"NORTE","name":"Norte"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-16",
		"status":{"code":"CREATED","label":"Creada"},"lines":null},
		{"id":"o4","number":"ORD-4","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-16",
		"status":{"code":"VALIDATED","label":"Validada"},"lines":null}],
		"total":4,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	// The target that needs the material, and the closed order that must never be asked for.
	private val target = """
		{"id":"o2","number":"ORD-2","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-16",
		"status":{"code":"CREATED","label":"Creada"},"lines":[
		{"id":"l2","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"20.000000","pickedQuantity":"0.000000"}]}
	""".trimIndent()

	private val closed = """
		{"id":"o4","number":"ORD-4","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-16",
		"status":{"code":"VALIDATED","label":"Validada"},"lines":[
		{"id":"l4","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"20.000000","pickedQuantity":"0.000000"}]}
	""".trimIndent()

	private val transferred = """
		{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-10-15",
		"status":{"code":"FULFILLING","label":"Surtiendo"},"lines":[
		{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"30.000000","pickedQuantity":"0.000000"}]}
	""".trimIndent()

	// A material of the order that nothing has picked yet.
	private fun lookup(id: String = "p1") = """
		{"match":"CODE","products":[{"id":"$id","code":"$id","name":"Codo galvanizado","barcode":"$id",
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"stock":"100.000000"}]}
	""".trimIndent()

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	private fun MockRequestHandleScope.route(request: HttpRequestData): HttpResponseData = when {
		request.url.encodedPath == "/api/v1/orders/alerts" -> reply(alerts)
		request.url.encodedPath == "/api/v1/orders/query" -> reply(candidates)
		request.url.encodedPath == "/api/v1/products/lookup" -> reply(lookup(if (request.url.parameters["value"] == "M2") "p2" else "p1"))
		request.url.encodedPath == "/api/v1/orders/o1" -> reply(source)
		request.url.encodedPath == "/api/v1/orders/o2" -> reply(target)
		request.url.encodedPath == "/api/v1/orders/o4" -> reply(closed)
		request.url.encodedPath == "/api/v1/orders/o1/reassignments/transfer" -> reply(transferred)
		else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
	}

	private fun viewModel(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): OrderReassignmentViewModel {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@OrderReassignmentViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return OrderReassignmentViewModel(
			OrdersApi(engine, "http://test/api/v1") { Language.ES },
			ProductApi(engine, "http://test/api/v1") { Language.ES },
			session,
			{ "obligatorio" },
			{ "cantidad inválida" },
			{ "nada surtido de ese material" },
			{ "más de lo surtido" },
			{ "la orden destino no necesita esa cantidad" },
			{ "sin conexión" },
		)
	}

	// The form of one alert, open and with its material scanned.
	private suspend fun TestScope.opened(view: OrderReassignmentViewModel) {
		view.load()
		advanceUntilIdle()
		view.open(view.state.value.alerts.single())
		advanceUntilIdle()
		view.scanned("M1")
		advanceUntilIdle()
	}

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun theAlertsOfTheSignedInPersonAreListedWithTheirKind() = runTest {
		val view = viewModel { route(it) }
		view.load()
		advanceUntilIdle()
		val alert = view.state.value.alerts.single()
		assertEquals("ORD-1", alert.order.number)
		assertEquals("BEFORE", alert.kind)
		assertEquals("2026-10-15", alert.order.dueDate)
	}

	@Test
	fun theScannedMaterialOpensItsPickedLineAndOnlyTheOrdersOfItsWarehouseThatNeedIt() = runTest {
		val view = viewModel { route(it) }
		opened(view)
		val state = view.state.value
		assertEquals("l1", state.line?.id)
		assertEquals("20.000000", state.line?.pickedQuantity)
		// The source order, another warehouse's order and a validated one are not offered.
		assertEquals(listOf("ORD-2"), state.targets.map { it.order.number })
		assertEquals("20.000000", state.targets.single().line.remaining)
		assertTrue(requests.none { it.url.encodedPath == "/api/v1/orders/o4" }, "the closed order is not even asked for")
		assertTrue(requests.none { it.url.encodedPath == "/api/v1/orders/o3" }, "another warehouse's order is not asked for")
	}

	@Test
	fun aMaterialTheOrderHasNotPickedYetIsRefusedAndOffersNoTarget() = runTest {
		val view = viewModel { route(it) }
		view.load()
		advanceUntilIdle()
		view.open(view.state.value.alerts.single())
		advanceUntilIdle()
		view.scanned("M2")
		advanceUntilIdle()
		assertNull(view.state.value.line)
		assertEquals(emptyList<ReassignmentTarget>(), view.state.value.targets)
		assertEquals("nada surtido de ese material", view.state.value.scanError)
	}

	@Test
	fun theQuantityIsKeptWithinWhatIsPickedAndWhatTheTargetNeeds() = runTest {
		val view = viewModel { route(it) }
		opened(view)
		view.choose(view.state.value.targets.single())
		view.editNote("Pasa a la otra orden")

		// More than the target needs (20), and more than the line picked (20): both are refused here.
		view.editQuantity("30")
		view.save()
		advanceUntilIdle()
		assertEquals("más de lo surtido", view.state.value.quantityError)
		assertTrue(requests.none { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/reassignments/transfer") })

		// The note is required, and the quantity reaches the API when it fits.
		view.editQuantity("20")
		view.editNote("")
		view.save()
		advanceUntilIdle()
		assertEquals("obligatorio", view.state.value.noteError)
		assertTrue(requests.none { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/reassignments/transfer") })

		view.editNote("Pasa a la otra orden")
		view.save()
		advanceUntilIdle()
		val moved = requests.single { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/reassignments/transfer") }
		assertTrue(moved.bodyText().contains("\"targetOrderId\":\"o2\""), moved.bodyText())
		assertTrue(moved.bodyText().contains("\"quantity\":\"20\""), moved.bodyText())
		assertNotNull(view.state.value.saved)
	}

	@Test
	fun aQuantityAboveWhatTheTargetNeedsIsRefused() = runTest {
		val view = viewModel { route(it) }
		opened(view)
		// A line of 30 with 20 picked: 25 is within what is picked, but above the 20 the target needs.
		view.choose(view.state.value.targets.single().copy(line = view.state.value.targets.single().line.copy(quantity = "20")))
		view.editQuantity("25")
		view.editNote("Pasa")
		view.save()
		advanceUntilIdle()
		assertTrue(view.state.value.quantityError == "la orden destino no necesita esa cantidad" || view.state.value.quantityError == "más de lo surtido")
	}

	private fun HttpRequestData.bodyText(): String {
		val body = this.body as? io.ktor.http.content.TextContent ?: return ""
		return body.text
	}
}
