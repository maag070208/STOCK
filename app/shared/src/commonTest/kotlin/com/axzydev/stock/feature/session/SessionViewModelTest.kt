package com.axzydev.stock.feature.session

import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.session.AuthApi
import com.axzydev.stock.data.session.TokenStorage
import com.axzydev.stock.i18n.Language
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// F1-20 · unit: the session store keeps the tokens, clears them on sign-out, and the app follows the API's answers.
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

	private class MemoryStorage(var token: String? = null) : TokenStorage {
		override fun read() = token
		override fun write(token: String) { this.token = token }
		override fun clear() { token = null }
	}

	private val requests = mutableListOf<HttpRequestData>()

	private fun user(mustChange: Boolean) =
		"""{"id":"u-1","username":"ana","name":"Ana López","language":"es","mustChangePassword":$mustChange,"roles":["APP_USER"],"permissions":{"app.access":"ALL"}}"""

	// The app's coroutines and the fake API run on the test's dispatcher, so every answer has arrived when a call returns.
	private val dispatcher = UnconfinedTestDispatcher()

	private fun viewModel(storage: TokenStorage, route: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SessionViewModel {
		val engine = MockEngine(MockEngineConfig().apply {
			dispatcher = this@SessionViewModelTest.dispatcher
			addHandler { request -> requests += request; route(request) }
		})
		return SessionViewModel(AuthApi(engine, "http://test/api/v1") { Language.ES }, storage) { "Sin conexión con la API" }
	}

	private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
		respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

	@BeforeTest
	fun setUp() = Dispatchers.setMain(dispatcher)

	@AfterTest
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun signingInKeepsTheRefreshTokenAndOpensTheHome() = runTest {
		val storage = MemoryStorage()
		val session = viewModel(storage) { reply("""{"token":"access-1","refreshToken":"refresh-1","user":${user(false)}}""") }
		session.signIn("  ana ", "secret-password")
		advanceUntilIdle()
		val state = assertIs<SessionState.SignedIn>(session.state.value)
		assertEquals("Ana López", state.user.name)
		assertEquals("refresh-1", storage.token)
		val login = requests.single()
		assertEquals("app", login.headers["X-Client-Channel"])
		assertEquals("es", login.headers[HttpHeaders.AcceptLanguage])
		assertTrue((login.body as TextContent).text.contains("\"username\":\"ana\""))
	}

	@Test
	fun aTemporaryPasswordLeadsToThePasswordChange() = runTest {
		val session = viewModel(MemoryStorage()) { reply("""{"token":"a","refreshToken":"r","user":${user(true)}}""") }
		session.signIn("ana", "temporary-pass")
		advanceUntilIdle()
		assertIs<SessionState.PasswordChange>(session.state.value)
	}

	@Test
	fun aRefusalShowsTheApiMessageAndKeepsNothing() = runTest {
		val storage = MemoryStorage()
		val session = viewModel(storage) {
			reply("""{"error":"Forbidden","code":"INSUFFICIENT_PERMISSIONS","message":"No tienes permiso para realizar esta acción.","details":[]}""", HttpStatusCode.Forbidden)
		}
		session.signIn("ana", "secret-password")
		advanceUntilIdle()
		assertEquals(SessionState.SignedOut(error = "No tienes permiso para realizar esta acción."), session.state.value)
		assertNull(storage.token)
	}

	@Test
	fun anUnreachableApiShowsTheAppsOwnMessage() = runTest {
		val session = viewModel(MemoryStorage()) { throw IOException("connection refused") }
		session.signIn("ana", "secret-password")
		advanceUntilIdle()
		assertEquals(SessionState.SignedOut(error = "Sin conexión con la API"), session.state.value)
	}

	@Test
	fun aSavedSessionIsRestoredWithANewRefreshToken() = runTest {
		val storage = MemoryStorage("refresh-old")
		val session = viewModel(storage) { request ->
			if (request.url.encodedPath.endsWith("/auth/refresh")) reply("""{"token":"access-2","refreshToken":"refresh-new"}""")
			else reply("""{"user":${user(false)}}""")
		}
		session.restore()
		advanceUntilIdle()
		assertIs<SessionState.SignedIn>(session.state.value)
		assertEquals("refresh-new", storage.token)
		assertEquals("Bearer access-2", requests.last().headers[HttpHeaders.Authorization])
	}

	@Test
	fun aRevokedSessionIsForgottenOnRestore() = runTest {
		val storage = MemoryStorage("refresh-old")
		val session = viewModel(storage) { reply("""{"code":"TOKEN_REVOKED","message":"La sesión ya no es válida."}""", HttpStatusCode.Unauthorized) }
		session.restore()
		advanceUntilIdle()
		assertEquals(SessionState.SignedOut(), session.state.value)
		assertNull(storage.token)
	}

	@Test
	fun withoutASavedSessionTheSignInIsShown() = runTest {
		val session = viewModel(MemoryStorage()) { error("no request expected") }
		session.restore()
		advanceUntilIdle()
		assertEquals(SessionState.SignedOut(), session.state.value)
		assertTrue(requests.isEmpty())
	}

	@Test
	fun signingOutClearsTheTokensAndTellsTheApi() = runTest {
		val storage = MemoryStorage()
		val session = viewModel(storage) { request ->
			if (request.url.encodedPath.endsWith("/auth/logout")) respond("", HttpStatusCode.NoContent)
			else reply("""{"token":"access-1","refreshToken":"refresh-1","user":${user(false)}}""")
		}
		session.signIn("ana", "secret-password")
		advanceUntilIdle()
		session.signOut()
		assertEquals(SessionState.SignedOut(), session.state.value)
		assertNull(storage.token)
		val logout = requests.last()
		assertTrue(logout.url.encodedPath.endsWith("/auth/logout"))
		assertTrue((logout.body as TextContent).text.contains("refresh-1"))
	}

	@Test
	fun anExpiredAccessTokenIsRefreshedOnceAndTheCallRepeated() = runTest {
		val storage = MemoryStorage()
		val session = viewModel(storage) { request ->
			if (request.url.encodedPath.endsWith("/auth/refresh")) reply("""{"token":"access-2","refreshToken":"refresh-2"}""")
			else reply("""{"token":"access-1","refreshToken":"refresh-1","user":${user(false)}}""")
		}
		session.signIn("ana", "secret-password")
		advanceUntilIdle()
		val used = mutableListOf<String>()
		val answer = session.withToken { token ->
			used += token
			if (token == "access-1") throw ApiError(401, "TOKEN_EXPIRED", "La sesión expiró.")
			"products"
		}
		assertEquals("products", answer)
		assertEquals(listOf("access-1", "access-2"), used)
		assertEquals("refresh-2", storage.token)
		assertEquals(1, requests.count { it.url.encodedPath.endsWith("/auth/refresh") })
	}

	@Test
	fun aSessionTheApiEndedSignsThePersonOut() = runTest {
		val storage = MemoryStorage()
		val session = viewModel(storage) { request ->
			if (request.url.encodedPath.endsWith("/auth/refresh")) reply("""{"code":"TOKEN_REVOKED","message":"La sesión ya no es válida."}""", HttpStatusCode.Unauthorized)
			else reply("""{"token":"access-1","refreshToken":"refresh-1","user":${user(false)}}""")
		}
		session.signIn("ana", "secret-password")
		advanceUntilIdle()
		val refused = runCatching { session.withToken<String> { throw ApiError(401, "TOKEN_EXPIRED", "La sesión expiró.") } }
		assertIs<ApiError>(refused.exceptionOrNull())
		assertEquals(SessionState.SignedOut(), session.state.value)
		assertNull(storage.token)
	}

	@Test
	fun aRefusedPasswordChangeNamesTheField() = runTest {
		val session = viewModel(MemoryStorage()) { request ->
			if (request.url.encodedPath.endsWith("/auth/change-password")) {
				reply("""{"code":"PASSWORD_INVALID","message":"La contraseña debe tener entre 5 y 72 caracteres.","details":[{"field":"newPassword","code":"PASSWORD_INVALID","message":"La contraseña debe tener entre 5 y 72 caracteres."}]}""", HttpStatusCode.BadRequest)
			} else reply("""{"token":"a","refreshToken":"r","user":${user(true)}}""")
		}
		session.signIn("ana", "temporary-pass")
		advanceUntilIdle()
		session.changePassword("temporary-pass", "short")
		val state = assertIs<SessionState.PasswordChange>(session.state.value)
		assertEquals(mapOf("newPassword" to "La contraseña debe tener entre 5 y 72 caracteres."), state.fieldErrors)
	}

	@Test
	fun aPasswordChangeOpensTheHomeWithTheNewSession() = runTest {
		val storage = MemoryStorage()
		var changed = false
		val session = viewModel(storage) { request ->
			when {
				request.url.encodedPath.endsWith("/auth/change-password") -> { changed = true; reply("""{"token":"access-2","refreshToken":"refresh-2"}""") }
				request.url.encodedPath.endsWith("/auth/me") -> reply("""{"user":${user(!changed)}}""")
				else -> reply("""{"token":"a","refreshToken":"r","user":${user(true)}}""")
			}
		}
		session.signIn("ana", "temporary-pass")
		advanceUntilIdle()
		session.changePassword("temporary-pass", "my-own-password")
		assertIs<SessionState.SignedIn>(session.state.value)
		assertEquals("refresh-2", storage.token)
	}
}
