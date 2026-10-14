package com.axzydev.stock.feature.receipts

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.locations.LocationsApi
import com.axzydev.stock.data.receipts.ReceivingApi
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
import kotlin.test.assertTrue

// F4-23 · unit: the placement view model keeps the placed total equal to the counted quantity. The operator scans the
// location of each part; a location of another warehouse is refused here, and a sum that is not the counted quantity
// never reaches the API.
@OptIn(ExperimentalCoroutinesApi::class)
class PlacementViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun apis(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<ReceivingApi, LocationsApi> {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@PlacementViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return ReceivingApi(engine, "http://test/api/v1") { Language.ES } to LocationsApi(engine, "http://test/api/v1") { Language.ES }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	// One counted assignment, matched and not placed yet: 80 counted of 80.
	private val mine = """
		[{"invoice":{"id":"inv-1","number":"F-100","supplier":{"id":"s1","code":"PROV1","name":"Aceros SA"},"contact":null,
		"invoiceDate":"2026-01-10","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"status":{"code":"IN_ASSIGNMENT","label":"En asignación"},"createdAt":"2026-01-10T10:00:00Z","lines":null},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"80.000000",
		"assignments":[{"id":"a1","lineId":"l1","operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"expectedQuantity":"80.000000","countedQuantity":"80.000000","difference":"0.000000","outcome":"MATCH",
		"countNotes":null,"countedAt":"2026-01-10T11:00:00Z","placedQuantity":"0.000000","placed":false,"placements":[]}]}]}]
	""".trimIndent()

	// Two locations of the invoice's warehouse, for any code asked.
	private val locations = """
		{"data":[{"id":"loc-1","warehouseId":"w1","warehouseCode":"CENTRAL","code":"K05A","aisle":"K","rack":"05","level":"A","temporary":false,"active":true},
		{"id":"loc-2","warehouseId":"w1","warehouseCode":"CENTRAL","code":"K05B","aisle":"K","rack":"05","level":"B","temporary":false,"active":true}],
		"total":2,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	private val placed = """
		{"id":"a1","lineId":"l1","operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"expectedQuantity":"80.000000","countedQuantity":"80.000000","difference":"0.000000","outcome":"MATCH",
		"countNotes":null,"countedAt":"2026-01-10T11:00:00Z","placedQuantity":"80.000000","placed":true,
		"placements":[{"id":"pl1","locationId":"loc-1","locationCode":"K05A","quantity":"50.000000","placedAt":"2026-01-10T12:00:00Z"},
		{"id":"pl2","locationId":"loc-2","locationCode":"K05B","quantity":"30.000000","placedAt":"2026-01-10T12:00:00Z"}]}
	""".trimIndent()

	private fun MockRequestHandleScope.route(request: HttpRequestData): HttpResponseData = when {
		request.url.encodedPath == "/api/v1/receipts/mine" -> reply(mine)
		request.url.encodedPath == "/api/v1/locations/query" -> reply(locations)
		request.url.encodedPath.endsWith("/placements") -> reply(placed)
		else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
	}

	private fun viewModel(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): PlacementViewModel {
		val (receiving, locationsApi) = apis(route)
		return PlacementViewModel(
			receiving,
			locationsApi,
			session,
			{ "obligatorio" },
			{ "cantidad no válida" },
			{ "no en este almacén" },
			{ "ubica todo lo contado" },
		) { "sin conexión" }
	}

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun twoLocationsAreScannedAndTheirSumIsTheCountedQuantity() = runTest {
		val view = viewModel { route(it) }
		view.load()
		advanceUntilIdle()
		val target = view.state.value.targets.single()
		assertEquals("a1", target.assignment.id)
		view.open(target)
		assertEquals("80.000000", view.state.value.remaining)
		view.scanned("K05A")
		advanceUntilIdle()
		// The first row is prefilled with everything that is left.
		assertEquals("80.000000", view.state.value.lines.single().quantity)
		view.editQuantity(0, "50")
		view.scanned("K05B")
		advanceUntilIdle()
		// The second row is prefilled with what remains after the first.
		assertEquals("30.000000", view.state.value.lines[1].quantity)
		view.save()
		advanceUntilIdle()
		val request = requests.first { it.url.encodedPath.endsWith("/placements") }
		assertEquals(HttpMethod.Post, request.method)
		assertEquals("/api/v1/receipts/assignments/a1/placements", request.url.encodedPath)
		assertEquals(true, view.state.value.saved?.placed)
	}

	@Test
	fun aTotalThatIsNotTheCountedQuantityIsRefused() = runTest {
		val view = viewModel { route(it) }
		view.load()
		advanceUntilIdle()
		view.open(view.state.value.targets.single())
		view.scanned("K05A")
		advanceUntilIdle()
		view.editQuantity(0, "50")
		view.save()
		advanceUntilIdle()
		assertEquals("ubica todo lo contado", view.state.value.lineError)
		assertTrue(requests.none { it.url.encodedPath.endsWith("/placements") })
	}

	@Test
	fun aLocationOfAnotherWarehouseIsRefused() = runTest {
		val view = viewModel { request ->
			when {
				request.url.encodedPath == "/api/v1/receipts/mine" -> reply(mine)
				request.url.encodedPath == "/api/v1/locations/query" -> reply("""{"data":[],"total":0,"page":0,"pageIndex":0,"totalPages":0}""")
				else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
			}
		}
		view.load()
		advanceUntilIdle()
		view.open(view.state.value.targets.single())
		view.scanned("W99Z")
		advanceUntilIdle()
		assertEquals("no en este almacén", view.state.value.scanError)
		assertTrue(view.state.value.lines.isEmpty())
	}
}
