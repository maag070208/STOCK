package com.axzydev.stock.feature.customers

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.customers.CustomersApi
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

// The customers page reads the API's table contract and shows the API's own message when it refuses.
@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun api(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CustomersApi {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@CustomersViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return CustomersApi(engine, "http://test/api/v1") { Language.ES }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	private val oneCustomer = """
		{"data":[{"id":"c1","code":"CLI-1","name":"Ferretería del Norte","taxId":"FNO120101ABC",
		"address":{"street":"Av. Juárez","exteriorNumber":"120","interiorNumber":null,"neighborhood":"Centro",
		"city":"Monterrey","state":{"code":"MX_NL","label":"Nuevo León"},"postalCode":"64000",
		"country":{"code":"MX","label":"México"}},"active":true}],
		"total":1,"page":0,"pageIndex":0,"totalPages":1}
	""".trimIndent()

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun customersAreReadWithTheSessionAndTheirCity() = runTest {
		val customers = CustomersViewModel(api { reply(oneCustomer) }, session) { "Sin conexión con la API" }
		customers.load()
		advanceUntilIdle()
		val request = requests.single()
		assertEquals(HttpMethod.Post, request.method)
		assertEquals("/api/v1/customers/query", request.url.encodedPath)
		assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
		assertEquals("app", request.headers["X-Client-Channel"])
		val row = customers.state.value.rows.single()
		assertEquals("CLI-1", row.code)
		assertEquals("Ferretería del Norte", row.name)
		assertEquals("FNO120101ABC", row.taxId)
		// The rest of the address is ignored; only the city is shown.
		assertEquals("Monterrey", row.address?.city)
		assertTrue(row.active)
		assertNull(customers.state.value.error)
	}

	@Test
	fun aReaderWithoutThePermissionSeesTheMessageOfTheApi() = runTest {
		val customers = CustomersViewModel(api {
			reply("""{"code":"FORBIDDEN","message":"No tienes permiso para ver esto.","details":[]}""", HttpStatusCode.Forbidden)
		}, session) { "Sin conexión con la API" }
		customers.load()
		advanceUntilIdle()
		assertEquals("No tienes permiso para ver esto.", customers.state.value.error)
	}

	@Test
	fun withoutTheApiTheAppSaysSo() = runTest {
		val customers = CustomersViewModel(api { throw kotlinx.io.IOException("offline") }, session) { "Sin conexión con la API" }
		customers.load()
		advanceUntilIdle()
		assertEquals("Sin conexión con la API", customers.state.value.error)
	}
}
