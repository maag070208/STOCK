package com.axzydev.stock.feature.orders

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.catalogs.CatalogApi
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The states of an order come from the API's own ORDER_STATUS catalog, in the language of the request, and the
// `terminal` attribute says which one ends the order.
@OptIn(ExperimentalCoroutinesApi::class)
class OrderStatusesViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun api(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CatalogApi {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@OrderStatusesViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return CatalogApi(engine, "http://test/api/v1") { Language.ES }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	private val statuses = """
		[{"code":"CREATED","label":"Creada","description":null,"sortOrder":1,"active":true,"attributes":{},"labels":{}},
		{"code":"FULFILLING","label":"Surtiendo","description":null,"sortOrder":2,"active":true,"attributes":{},"labels":{}},
		{"code":"VALIDATED","label":"Validada","description":null,"sortOrder":4,"active":true,"attributes":{"terminal":true},"labels":{}},
		{"code":"CANCELLED","label":"Cancelada","description":null,"sortOrder":5,"active":true,"attributes":{"terminal":"true"},"labels":{}}]
	""".trimIndent()

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun theStatesAreReadFromTheCatalogOfTheApi() = runTest {
		val view = OrderStatusesViewModel(api { reply(statuses) }, session) { "Sin conexión con la API" }
		view.load()
		advanceUntilIdle()
		val request = requests.single()
		assertEquals(HttpMethod.Get, request.method)
		assertEquals("/api/v1/catalogs/ORDER_STATUS/items", request.url.encodedPath)
		assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
		assertEquals("es", request.headers[HttpHeaders.AcceptLanguage])
		assertEquals(listOf("CREATED", "FULFILLING", "VALIDATED", "CANCELLED"), view.state.value.rows.map { it.code })
		assertEquals("Surtiendo", view.state.value.rows[1].label)
	}

	@Test
	fun onlyTheStatesThatEndTheOrderAreTerminal() = runTest {
		val view = OrderStatusesViewModel(api { reply(statuses) }, session) { "Sin conexión con la API" }
		view.load()
		advanceUntilIdle()
		val terminal = view.state.value.rows.filter { it.isFlag("terminal") }.map { it.code }
		assertEquals(listOf("VALIDATED", "CANCELLED"), terminal)
		assertFalse(view.state.value.rows.first().isFlag("terminal"))
	}

	@Test
	fun aRefusalFromTheApiIsShownWithItsOwnMessage() = runTest {
		val view = OrderStatusesViewModel(api {
			reply("""{"code":"FORBIDDEN","message":"No tienes permiso para ver esto.","details":[]}""", HttpStatusCode.Forbidden)
		}, session) { "Sin conexión con la API" }
		view.load()
		advanceUntilIdle()
		assertEquals("No tienes permiso para ver esto.", view.state.value.error)
		assertTrue(view.state.value.rows.isEmpty())
	}
}
