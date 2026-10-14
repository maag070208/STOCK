package com.axzydev.stock.feature.receipts

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.products.ProductApi
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

// F4-22 · unit: the count view model finds the operator's open assignment of the scanned material, rejects a negative
// quantity, and sends the count with the assignment id. A material with no open assignment is refused here.
@OptIn(ExperimentalCoroutinesApi::class)
class CountViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun apis(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<ProductApi, ReceivingApi> {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@CountViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return ProductApi(engine, "http://test/api/v1") { Language.ES } to ReceivingApi(engine, "http://test/api/v1") { Language.ES }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	private val product = """
		{"match":"CODE","products":[{"id":"p1","code":"M1","name":"Codo galvanizado","barcode":"M1",
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"stock":null}]}
	""".trimIndent()

	private val mine = """
		[{"invoice":{"id":"inv-1","number":"F-100","supplier":{"id":"s1","code":"PROV1","name":"Aceros SA"},"contact":null,
		"invoiceDate":"2026-01-10","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"status":{"code":"PENDING","label":"Pendiente"},"createdAt":"2026-01-10T10:00:00Z","lines":null},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"10.000000",
		"assignments":[{"id":"a1","lineId":"l1","operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"expectedQuantity":"10.000000","countedQuantity":null,"difference":null,"outcome":null,
		"countNotes":null,"countedAt":null,"placedQuantity":"0.000000","placed":false,"placements":[]}]}]}]
	""".trimIndent()

	private val counted = """
		{"id":"a1","lineId":"l1","operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"expectedQuantity":"10.000000","countedQuantity":"8.000000","difference":"-2.000000","outcome":"DISCREPANCY",
		"countNotes":null,"countedAt":"2026-01-10T11:00:00Z","placedQuantity":"0.000000","placed":false,"placements":[]}
	""".trimIndent()

	private fun MockRequestHandleScope.route(request: HttpRequestData): HttpResponseData = when {
		request.url.encodedPath == "/api/v1/products/lookup" -> reply(product)
		request.url.encodedPath == "/api/v1/receipts/mine" -> reply(mine)
		request.url.encodedPath.endsWith("/count") -> reply(counted)
		else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
	}

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun aScannedMaterialOpensItsOpenAssignmentAndCountsIt() = runTest {
		val (products, receiving) = apis { route(it) }
		val view = CountViewModel(products, receiving, session, { "obligatorio" }, { "cantidad no válida" }, { "no en asignación" }) { "sin conexión" }
		view.scanned("M1")
		advanceUntilIdle()
		// The material is in exactly one open assignment, so it is opened at once.
		val target = view.state.value.target
		assertNotNull(target)
		assertEquals("a1", target.assignment.id)
		assertEquals("F-100", target.invoice)
		view.editQuantity("8")
		view.save()
		advanceUntilIdle()
		val count = requests.first { it.url.encodedPath.endsWith("/count") }
		assertEquals(HttpMethod.Post, count.method)
		assertEquals("/api/v1/receipts/assignments/a1/count", count.url.encodedPath)
		assertEquals("Bearer access-1", count.headers[HttpHeaders.Authorization])
		assertEquals("DISCREPANCY", view.state.value.saved?.outcome)
	}

	@Test
	fun aNegativeQuantityIsRejectedAndNothingReachesTheApi() = runTest {
		val (products, receiving) = apis { route(it) }
		val view = CountViewModel(products, receiving, session, { "obligatorio" }, { "cantidad no válida" }, { "no en asignación" }) { "sin conexión" }
		view.scanned("M1")
		advanceUntilIdle()
		view.editQuantity("-5")
		view.save()
		advanceUntilIdle()
		assertEquals("cantidad no válida", view.state.value.quantityError)
		assertTrue(requests.none { it.url.encodedPath.endsWith("/count") })
		// A blank is not a quantity either, and it asks for one.
		view.editQuantity("  ")
		view.save()
		assertEquals("obligatorio", view.state.value.quantityError)
	}

	@Test
	fun aMaterialNotInAnOpenAssignmentIsRefused() = runTest {
		val (products, receiving) = apis { request ->
			when {
				request.url.encodedPath == "/api/v1/products/lookup" -> reply(
					"""{"match":"CODE","products":[{"id":"p99","code":"Z9","name":"Otro material","barcode":"Z9",
					"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"stock":null}]}""".trimIndent(),
				)
				request.url.encodedPath == "/api/v1/receipts/mine" -> reply(mine)
				else -> reply("""{"code":"NOT_FOUND","message":"no route","details":[]}""", HttpStatusCode.NotFound)
			}
		}
		val view = CountViewModel(products, receiving, session, { "obligatorio" }, { "cantidad no válida" }, { "no en asignación" }) { "sin conexión" }
		// The lookup answers a material that the operator's open assignments do not hold.
		view.scanned("Z9")
		advanceUntilIdle()
		assertEquals("no en asignación", view.state.value.error)
		assertTrue(view.state.value.target == null)
	}
}
