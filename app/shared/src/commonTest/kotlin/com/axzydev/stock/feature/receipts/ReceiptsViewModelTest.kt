package com.axzydev.stock.feature.receipts

import com.axzydev.stock.data.api.Authorized
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

// F5 · unit: the pages of the receiving module read their list with the session, keep the digits the API sent, and
// show the API's own message when it refuses. Nothing is written: the app counts and places with the API's own
// operations, not from a list screen.
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptsViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun api(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ReceivingApi {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@ReceiptsViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return ReceivingApi(engine, "http://test/api/v1") { Language.ES }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	// One invoice with one line and one assignment: counted 8 of 10, so short by 2.
	private val mine = """
		[{"invoice":{"id":"inv-1","number":"F-100","supplier":{"id":"s1","code":"PROV1","name":"Aceros SA"},"contact":null,
		"invoiceDate":"2026-01-10","warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},
		"status":{"code":"RECEIVING","label":"En recepción"},"createdAt":"2026-01-10T10:00:00Z","lines":null},
		"lines":[{"id":"l1","product":{"id":"p1","code":"M1","name":"Codo galvanizado"},
		"unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"quantity":"10.000000",
		"assignments":[{"id":"a1","lineId":"l1","operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},
		"expectedQuantity":"10.000000","countedQuantity":"8.000000","difference":"-2.000000","outcome":"DISCREPANCY",
		"countNotes":null,"countedAt":"2026-01-10T11:00:00Z","placedQuantity":"8.000000","placed":true,"placements":[]}]}]}]
	""".trimIndent()

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun myReceiptsAreReadWithTheSessionAndKeepTheDigitsOfTheApi() = runTest {
		val receipts = ReceiptsViewModel(api { reply(mine) }, session) { "Sin conexión con la API" }
		receipts.load()
		advanceUntilIdle()
		val request = requests.single()
		assertEquals(HttpMethod.Get, request.method)
		assertEquals("/api/v1/receipts/mine", request.url.encodedPath)
		assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
		assertEquals("app", request.headers["X-Client-Channel"])
		val row = receipts.state.value.rows.single()
		assertEquals("F-100", row.invoice.number)
		assertEquals("Aceros SA", row.invoice.supplier.name)
		assertEquals("CENTRAL", row.invoice.warehouse.code)
		val assignment = row.lines.single().assignments.single()
		// The quantity is shown as the API wrote it, never as a floating point number.
		assertEquals("10.000000", assignment.expectedQuantity)
		assertEquals("8.000000", assignment.countedQuantity)
		assertEquals("-2.000000", assignment.difference)
		assertNull(receipts.state.value.error)
	}

	@Test
	fun aReaderWithoutThePermissionSeesTheMessageOfTheApi() = runTest {
		val receipts = ReceiptsViewModel(api {
			reply("""{"code":"FORBIDDEN","message":"No tienes permiso para ver esto.","details":[]}""", HttpStatusCode.Forbidden)
		}, session) { "Sin conexión con la API" }
		receipts.load()
		advanceUntilIdle()
		assertEquals("No tienes permiso para ver esto.", receipts.state.value.error)
		assertTrue(receipts.state.value.rows.isEmpty())
	}

	@Test
	fun withoutTheApiTheAppSaysSoAndOffersARetry() = runTest {
		val receipts = ReceiptsViewModel(api { throw kotlinx.io.IOException("offline") }, session) { "Sin conexión con la API" }
		receipts.load()
		advanceUntilIdle()
		assertEquals("Sin conexión con la API", receipts.state.value.error)
	}

	@Test
	fun anEmptyAnswerIsAnEmptyListAndNotAnError() = runTest {
		val receipts = ReceiptsViewModel(api { reply("[]") }, session) { "Sin conexión con la API" }
		receipts.load()
		advanceUntilIdle()
		assertTrue(receipts.state.value.empty)
		assertNull(receipts.state.value.error)
	}

	@Test
	fun theDiscrepanciesAreReadWithTheTableContractOfTheApi() = runTest {
		val discrepancies = DiscrepanciesViewModel(api {
			reply(
				"""{"data":[{"assignmentId":"a1","invoice":{"id":"inv-1","code":"F-100","name":null},
				"warehouse":{"id":"w1","code":"CENTRAL","name":"Central"},"product":{"id":"p1","code":"M1","name":"Codo"},
				"operator":{"id":"u1","code":"aamaro","name":"A. Amaro"},"expectedQuantity":"10.000000",
				"countedQuantity":"8.000000","difference":"-2.000000","countNotes":null,"countedAt":"2026-01-10T11:00:00Z"}],
				"total":1,"page":0,"pageIndex":0,"totalPages":1}""".trimIndent(),
			)
		}, session) { "Sin conexión con la API" }
		discrepancies.load()
		advanceUntilIdle()
		val request = requests.single()
		assertEquals(HttpMethod.Post, request.method)
		assertEquals("/api/v1/receipts/discrepancies/query", request.url.encodedPath)
		assertEquals("-2.000000", discrepancies.state.value.rows.single().difference)
	}
}
