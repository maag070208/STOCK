package com.axzydev.stock.feature.orders

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.locations.LocationsApi
import com.axzydev.stock.data.orders.OrdersApi
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.stock.StockApi
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// F5-18 · unit: the pick view model opens the line of the scanned material, shows the locations that hold it, and keeps
// the quantity at or below the balance the API showed. A material that is not on the order, and a location that does not
// hold it, never reach the API.
@OptIn(ExperimentalCoroutinesApi::class)
class OrderPickViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	// The orders assigned to the operator, as the query answers them: the header, without the lines.
	private val orders = """
		{"data":[{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-01-20",
		"status":{"code":"CREATED","label":"Creada"},"operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},"lines":null}],
		"total":1,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	// The order read by id, with its line of 100 and nothing picked yet.
	private val fullOrder = """
		{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-01-20",
		"status":{"code":"CREATED","label":"Creada"},"operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"100.000000","pickedQuantity":"0.000000"}]}
	""".trimIndent()

	// Two locations of the order's warehouse hold the material; K09A of the same warehouse does not.
	private val balances = """
		{"data":[{"warehouseId":"w1","warehouseCode":"CENTRAL","locationId":"loc-1","locationCode":"K05A","productId":"p1","quantity":"80.000000"},
		{"warehouseId":"w1","warehouseCode":"CENTRAL","locationId":"loc-2","locationCode":"K05B","productId":"p1","quantity":"20.000000"}],
		"total":2,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	private val locations = """
		{"data":[{"id":"loc-1","warehouseId":"w1","warehouseCode":"CENTRAL","code":"K05A","aisle":"K","rack":"05","level":"A","temporary":false,"active":true},
		{"id":"loc-9","warehouseId":"w1","warehouseCode":"CENTRAL","code":"K09A","aisle":"K","rack":"09","level":"A","temporary":false,"active":true}],
		"total":2,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	private val order = """
		{"id":"o1","number":"ORD-1","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"customer":{"id":"c1","code":"CLI1","name":"Aceros SA"},"dueDate":"2026-01-20",
		"status":{"code":"FULFILLING","label":"Surtiendo"},"operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"100.000000","pickedQuantity":"80.000000"}]}
	""".trimIndent()

	// The material on the order, and one that is not on it.
	private fun lookup(match: String) = """
		{"match":"CODE","products":[{"id":"$match","code":"$match","name":"Codo galvanizado","barcode":"$match",
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"stock":"100.000000"}]}
	""".trimIndent()

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	private fun MockRequestHandleScope.route(request: HttpRequestData): HttpResponseData = when {
		request.url.encodedPath == "/api/v1/orders/query" -> reply(orders)
		request.url.encodedPath == "/api/v1/orders/o1" -> reply(fullOrder)
		request.url.encodedPath == "/api/v1/products/lookup" -> reply(lookup(if (request.url.parameters["value"] == "M2") "p2" else "p1"))
		request.url.encodedPath == "/api/v1/stock/balances/query" -> reply(balances)
		request.url.encodedPath == "/api/v1/locations/query" -> reply(locations)
		request.url.encodedPath == "/api/v1/orders/o1/picks" -> reply(order)
		else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
	}

	private fun viewModel(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): OrderPickViewModel {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@OrderPickViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		val ordersApi = OrdersApi(engine, "http://test/api/v1") { Language.ES }
		val productsApi = ProductApi(engine, "http://test/api/v1") { Language.ES }
		val stockApi = StockApi(engine, "http://test/api/v1") { Language.ES }
		val locationsApi = LocationsApi(engine, "http://test/api/v1") { Language.ES }
		return OrderPickViewModel(
			ordersApi, productsApi, stockApi, locationsApi, session, { "u1" },
			{ "obligatorio" }, { "cantidad inválida" }, { "no está en la orden" }, { "no es de este almacén" },
			{ "la ubicación no tiene el material" }, { "más que el saldo" }, { "más que la línea" }, { "sin conexión" },
		)
	}

	private fun HttpRequestData.picking() = url.encodedPath.endsWith("/picks")

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun theOpenOrdersOfTheOperatorAreListed() = runTest {
		val pick = viewModel { route(it) }
		pick.load()
		advanceUntilIdle()
		assertEquals(listOf("ORD-1"), pick.state.value.orders.map { it.number })
	}

	@Test
	fun aScannedMaterialOpensItsLineAndTheLocationsThatHoldIt() = runTest {
		val pick = viewModel { route(it) }
		pick.load()
		advanceUntilIdle()
		pick.open(pick.state.value.orders.single())
		advanceUntilIdle()
		pick.scanned("M1")
		advanceUntilIdle()
		val state = pick.state.value
		assertEquals("l1", state.line?.id)
		assertEquals(listOf("K05A", "K05B"), state.balances.map { it.locationCode })
		assertNull(state.scanError)
	}

	@Test
	fun aMaterialThatIsNotOnTheOrderIsRefused() = runTest {
		val pick = viewModel { route(it) }
		pick.load()
		advanceUntilIdle()
		pick.open(pick.state.value.orders.single())
		advanceUntilIdle()
		pick.scanned("M2")
		advanceUntilIdle()
		assertNull(pick.state.value.line)
		assertEquals("no está en la orden", pick.state.value.scanError)
		assertTrue(requests.none { it.picking() })
	}

	@Test
	fun theQuantityIsKeptAtOrBelowTheBalanceTheApiShowed() = runTest {
		val pick = viewModel { route(it) }
		pick.load()
		advanceUntilIdle()
		pick.open(pick.state.value.orders.single())
		advanceUntilIdle()
		pick.scanned("M1")
		advanceUntilIdle()
		pick.scannedLocation("K05A")
		advanceUntilIdle()
		assertEquals("K05A", pick.state.value.location?.locationCode)

		// One above the 80 the location holds: the app refuses it, and nothing is posted.
		pick.editQuantity("81")
		pick.save()
		advanceUntilIdle()
		assertEquals("más que el saldo", pick.state.value.quantityError)
		assertTrue(requests.none { it.picking() })

		// One above what the line still needs (100) is refused too, even inside the balance.
		pick.choose(pick.state.value.balances.first { it.locationCode == "K05A" }.copy(quantity = "500.000000"))
		pick.editQuantity("150")
		pick.save()
		advanceUntilIdle()
		assertEquals("más que la línea", pick.state.value.quantityError)
		assertTrue(requests.none { it.picking() })

		// The whole balance of the location is taken: the API answers the order as it stands.
		pick.choose(pick.state.value.balances.first { it.locationCode == "K05A" })
		pick.editQuantity("80")
		pick.save()
		advanceUntilIdle()
		assertEquals(1, requests.count { it.picking() })
		assertNotNull(pick.state.value.saved)
		assertEquals("FULFILLING", pick.state.value.saved?.status?.code)
	}

	@Test
	fun aLocationOfAnotherWarehouseAndOneThatHoldsNoneAreRefused() = runTest {
		val pick = viewModel { route(it) }
		pick.load()
		advanceUntilIdle()
		pick.open(pick.state.value.orders.single())
		advanceUntilIdle()
		pick.scanned("M1")
		advanceUntilIdle()
		// A location of the warehouse that holds none of the material.
		pick.scannedLocation("K09A")
		advanceUntilIdle()
		assertNull(pick.state.value.location)
		assertEquals("la ubicación no tiene el material", pick.state.value.locationError)
		// A code this warehouse does not have at all.
		pick.scannedLocation("Z99Z")
		advanceUntilIdle()
		assertNull(pick.state.value.location)
		assertEquals("no es de este almacén", pick.state.value.locationError)
		assertTrue(requests.none { it.picking() })
	}
}
