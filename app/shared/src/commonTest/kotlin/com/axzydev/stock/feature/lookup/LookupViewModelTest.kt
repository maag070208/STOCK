package com.axzydev.stock.feature.lookup

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.products.ProductApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

// F3-13 · unit: the view model turns a scanned value into a lookup request, and shows the candidates when there are
// several. Nothing is chosen for the person, and a scan never creates anything: the only request is the lookup.
@OptIn(ExperimentalCoroutinesApi::class)
class LookupViewModelTest {

	private val requests = mutableListOf<HttpRequestData>()
	private val dispatcher = UnconfinedTestDispatcher()

	// The session hands its token to the call, as the signed-in app does.
	private val session = object : Authorized {
		override suspend fun <T> withToken(call: suspend (token: String) -> T): T = call("access-1")
	}

	private fun viewModel(route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): LookupViewModel {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@LookupViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return LookupViewModel(ProductApi(engine, "http://test/api/v1") { Language.ES }, session, { "Este campo es obligatorio." }) { "Sin conexión con la API" }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	// Names carry inch marks, which JSON escapes.
	private fun product(id: String, name: String, stock: String = "null") =
		"""{"id":"$id","code":"C$id","name":"${name.replace("\"", "\\\"")}","barcode":"C$id","unit":{"code":"PIECE","label":"Pieza","decimalPlaces":0},"stock":$stock}"""

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun aScannedValueIsLookedUpWithTheSessionAndShowsItsProduct() = runTest {
		val lookup = viewModel { reply("""{"match":"BARCODE","products":[${product("1", "Codo galvanizado 1/2\"", "12.500000")}]}""") }
		lookup.scanned("  7501031311309 ")
		advanceUntilIdle()
		val request = requests.single()
		assertEquals("/api/v1/products/lookup", request.url.encodedPath)
		assertEquals("7501031311309", request.url.parameters["value"])
		assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
		assertEquals("app", request.headers["X-Client-Channel"])
		val state = lookup.state.value
		assertEquals("Codo galvanizado 1/2\"", state.product?.name)
		// The quantity keeps the digits the API sent.
		assertEquals("12.500000", state.product?.stock)
		assertTrue(state.candidates.isEmpty())
	}

	@Test
	fun theCameraReadingTheSameLabelAgainAsksOnce() = runTest {
		val lookup = viewModel { reply("""{"match":"CODE","products":[${product("1", "Tornillo")}]}""") }
		repeat(5) { lookup.scanned("C1") }
		advanceUntilIdle()
		assertEquals(1, requests.size)
		// After clearing, the same label is a new scan.
		lookup.clear()
		lookup.scanned("C1")
		advanceUntilIdle()
		assertEquals(2, requests.size)
	}

	@Test
	fun threeMatchesByNameAreCandidatesAndNoneIsChosen() = runTest {
		val lookup = viewModel { reply("""{"match":"NAME","products":[${product("1", "Abrazadera 1\"")},${product("2", "Abrazadera 2\"")},${product("3", "Abrazadera 3\"")}]}""") }
		lookup.edit("abrazadera")
		lookup.submit()
		advanceUntilIdle()
		assertEquals(3, lookup.state.value.candidates.size)
		assertNull(lookup.state.value.product)
		assertNull(lookup.state.value.candidates.first().stock)
		lookup.choose(lookup.state.value.candidates[1])
		assertEquals("2", lookup.state.value.product?.id)
		assertTrue(lookup.state.value.candidates.isEmpty())
	}

	@Test
	fun aValueThatMatchesNothingShowsTheMessageOfTheApi() = runTest {
		val lookup = viewModel {
			reply("""{"code":"BARCODE_NOT_FOUND","message":"No se encontró ningún material con ese código.","details":[]}""", HttpStatusCode.NotFound)
		}
		lookup.scanned("NADA")
		advanceUntilIdle()
		assertEquals("No se encontró ningún material con ese código.", lookup.state.value.error)
		assertNull(lookup.state.value.product)
	}

	@Test
	fun anEmptyValueIsNotSentAndAsksForOne() = runTest {
		val lookup = viewModel { reply("{}") }
		lookup.edit("   ")
		lookup.submit()
		lookup.scanned("")
		advanceUntilIdle()
		assertTrue(requests.isEmpty())
		assertEquals("Este campo es obligatorio.", lookup.state.value.fieldError)
	}

	@Test
	fun withoutTheApiTheAppSaysSo() = runTest {
		val lookup = viewModel { throw kotlinx.io.IOException("offline") }
		lookup.scanned("C1")
		advanceUntilIdle()
		assertEquals("Sin conexión con la API", lookup.state.value.error)
	}
}
