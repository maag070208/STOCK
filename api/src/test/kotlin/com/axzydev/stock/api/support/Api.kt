package com.axzydev.stock.api.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ApiResponse(val status: Int, val body: String) {
	val json: JsonNode by lazy { Api.mapper.readTree(body) }
	val code: String? get() = if (body.isBlank()) null else json.get("code")?.asString()
	fun text(field: String): String = json.get(field).asString()
}

// A file the API answers, such as the comprobante of an order: the bytes, and what the response says they are.
class ApiDownload(val status: Int, val contentType: String?, val contentDisposition: String?, val bytes: ByteArray) {
	val text: String get() = String(bytes, Charsets.UTF_8)
}

// The elements of a JSON array, as a Kotlin list.
val JsonNode.list: List<JsonNode> get() = values().toList()

class TestUser(val id: String, val username: String, val password: String, val token: String, val refreshToken: String)

// Calls the running API over HTTP, as a client would. Test data is created through the API, never in the database.
class Api(private val port: Int) {

	private val client = HttpClient.newHttpClient()

	fun call(
		method: String,
		path: String,
		token: String? = null,
		body: Any? = null,
		channel: String? = "web",
		headers: Map<String, String> = emptyMap(),
		raw: ByteArray? = null,
	): ApiResponse {
		val payload = when {
			raw != null -> HttpRequest.BodyPublishers.ofByteArray(raw)
			body == null -> HttpRequest.BodyPublishers.noBody()
			body is String -> HttpRequest.BodyPublishers.ofString(body)
			else -> HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
		}
		val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1$path")).method(method, payload)
		if (body != null) request.header("Content-Type", "application/json")
		if (token != null) request.header("Authorization", "Bearer $token")
		if (channel != null) request.header("X-Client-Channel", channel)
		headers.forEach(request::header)
		val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
		return ApiResponse(response.statusCode(), response.body())
	}

	fun get(path: String, token: String? = null, channel: String? = "web") = call("GET", path, token, channel = channel)
	fun post(path: String, token: String? = null, body: Any? = null, channel: String? = "web") = call("POST", path, token, body, channel)
	fun put(path: String, token: String? = null, body: Any? = null) = call("PUT", path, token, body)
	fun patch(path: String, token: String? = null, body: Any? = null) = call("PATCH", path, token, body)
	fun delete(path: String, token: String? = null) = call("DELETE", path, token)

	// A download: the bytes as they are, with what the response says about them.
	fun download(path: String, token: String? = null, channel: String? = "web"): ApiDownload {
		val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1$path")).GET()
		if (token != null) request.header("Authorization", "Bearer $token")
		if (channel != null) request.header("X-Client-Channel", channel)
		val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
		return ApiDownload(
			response.statusCode(),
			response.headers().firstValue("Content-Type").orElse(null),
			response.headers().firstValue("Content-Disposition").orElse(null),
			response.body(),
		)
	}

	// A download that carries a body, such as the export of a report: the bytes as they are.
	fun downloadPost(path: String, token: String?, body: Any?, channel: String? = "web"): ApiDownload {
		val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1$path"))
			.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
			.header("Content-Type", "application/json")
		if (token != null) request.header("Authorization", "Bearer $token")
		if (channel != null) request.header("X-Client-Channel", channel)
		val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
		return ApiDownload(
			response.statusCode(),
			response.headers().firstValue("Content-Type").orElse(null),
			response.headers().firstValue("Content-Disposition").orElse(null),
			response.body(),
		)
	}

	// Sends a file as a form, the way a browser uploads it: the file in `file`, and the other fields as text.
	fun upload(path: String, token: String?, file: ByteArray, fields: Map<String, String> = emptyMap(), filename: String = "inventario.xlsx"): ApiResponse {
		val boundary = "----stockflow" + UUID.randomUUID().toString().replace("-", "")
		val body = java.io.ByteArrayOutputStream()
		val line = { text: String -> body.write((text + "\r\n").toByteArray(Charsets.UTF_8)) }
		for ((name, value) in fields) {
			line("--$boundary")
			line("Content-Disposition: form-data; name=\"$name\"")
			line("")
			line(value)
		}
		line("--$boundary")
		line("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"")
		line("Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
		line("")
		body.write(file)
		line("")
		line("--$boundary--")
		return call("POST", path, token, headers = mapOf("Content-Type" to "multipart/form-data; boundary=$boundary"), raw = body.toByteArray())
	}

	fun login(username: String, password: String, channel: String = "web"): ApiResponse =
		post("/auth/login", body = mapOf("username" to username, "password" to password), channel = channel)

	// A session of the first administrator. The first call changes the bootstrap password, as a person would.
	fun adminToken(): String = adminTokens.computeIfAbsent(port) {
		val first = login("admin", BOOTSTRAP_PASSWORD)
		if (first.status == 200) {
			val changed = post("/auth/change-password", first.text("token"), mapOf("currentPassword" to BOOTSTRAP_PASSWORD, "newPassword" to ADMIN_PASSWORD))
			check(changed.status == 200) { "admin password change failed: ${changed.body}" }
			changed.text("token")
		} else {
			val again = login("admin", ADMIN_PASSWORD)
			check(again.status == 200) { "admin sign-in failed: ${again.body}" }
			again.text("token")
		}
	}

	// Creates a person with these roles and signs them in with a password of their own, on a channel their roles allow.
	fun newUser(vararg roles: String, channel: String = "web"): TestUser {
		val created = createUser(*roles)
		val username = created.json.get("user").get("username").asString()
		val temporary = created.text("temporaryPassword")
		val first = login(username, temporary, channel)
		check(first.status == 200) { "first sign-in failed: ${first.body}" }
		val password = "Password-" + UUID.randomUUID().toString().take(8)
		val changed = post("/auth/change-password", first.text("token"), mapOf("currentPassword" to temporary, "newPassword" to password), channel)
		check(changed.status == 200) { "password change failed: ${changed.body}" }
		return TestUser(created.json.get("user").get("id").asString(), username, password, changed.text("token"), changed.text("refreshToken"))
	}

	// Creates a person and leaves the temporary password in place. The response is the API's own.
	fun createUser(vararg roles: String, username: String = "u" + UUID.randomUUID().toString().replace("-", "").take(10)): ApiResponse {
		val created = post("/users", adminToken(), mapOf("username" to username, "name" to "Persona de prueba", "roles" to roles.toList()))
		check(created.status == 201) { "user creation failed: ${created.body}" }
		return created
	}

	// Creates an active warehouse with a unique code, and gives its id.
	fun createWarehouse(code: String = uniqueCode("W")): String {
		val created = post("/warehouses", adminToken(), mapOf("code" to code, "name" to "Almacén de prueba"))
		check(created.status == 201) { "warehouse creation failed: ${created.body}" }
		return created.text("id")
	}

	// A payment term for suppliers and customers. The owner has not given the real ones, so the tests add their own,
	// marked as test data. It is created once; later calls find it.
	fun paymentTerms(): String {
		val labels = mapOf("es" to mapOf("label" to "Plazo de prueba"), "en" to mapOf("label" to "Test term"))
		val created = post("/catalogs/PAYMENT_TERMS/items", adminToken(), mapOf("code" to TEST_TERMS, "labels" to labels))
		check(created.status == 201 || created.code == "DUPLICATE_VALUE") { "payment term creation failed: ${created.body}" }
		return TEST_TERMS
	}

	// A supplier or a customer that passes every rule, with what the test changes on top.
	fun partner(code: String = uniqueCode("PR"), changes: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
		"code" to code,
		"name" to "Aceros del Norte",
		"taxId" to "ADN010203AB1",
		"address" to mapOf(
			"street" to "Av. Juárez", "exteriorNumber" to "120", "interiorNumber" to "B", "neighborhood" to "Centro",
			"city" to "Guadalajara", "state" to "MX_JAL", "postalCode" to "44100", "country" to "MX",
		),
		"paymentTerms" to paymentTerms(),
	) + changes

	companion object {
		const val TEST_TERMS = "E2E_TERMS"

		// A code no other test uses: the prefix, a hyphen and ten random letters and digits.
		fun uniqueCode(prefix: String) = prefix + "-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

		val mapper: JsonMapper = JsonMapper.builder().build()
		const val BOOTSTRAP_PASSWORD = "Bootstrap-Admin-01"
		const val ADMIN_PASSWORD = "Admin-Password-01"
		private val adminTokens = ConcurrentHashMap<Int, String>()
	}
}
