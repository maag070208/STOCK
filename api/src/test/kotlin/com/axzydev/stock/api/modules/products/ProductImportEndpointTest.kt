package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.support.ApiResponse
import com.axzydev.stock.api.support.ApiTest
import com.axzydev.stock.api.support.Xlsx
import com.axzydev.stock.api.support.list
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

// F2-07 and F2-08 · e2e: the import of the legacy file. The dry run reports and writes nothing; the confirmation
// writes in one transaction, only the file that was reviewed, and running it again writes nothing new.
class ProductImportEndpointTest : ApiTest() {

	// A small anonymized file with the legacy shape: its own products, a stray note with the control total.
	// Codes and legacy ids are this test's own, so it never meets another test's products or the real ones (1 to 1,398).
	private fun fixture(names: List<String> = listOf("ABRAZADERA  DE ARRANQUE 1-5/8\"", "VARILLA SINFIN GALV. 5/16\" X 1 MT", "ADORNO MALEABLE #10")): Pair<ByteArray, List<String>> {
		val prefix = "T" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
		val firstId = Random.nextInt(2_000_000, 2_000_000_000)
		val codes = names.indices.map { "$prefix$it" }
		val rows = names.mapIndexed { i, name -> Xlsx.product(firstId + i, codes[i], name, stock = (i + 1) * 10) }
		val total = rows.sumOf { (it["F"] as Int).toLong() }
		return Xlsx.build(listOf(Xlsx.HEADER) + rows + listOf(mapOf()) + Xlsx.control(total)) to codes
	}

	private fun dryRun(file: ByteArray, token: String = api.adminToken()) = api.upload("/products/import/dry-run", token, file)

	private fun confirm(file: ByteArray, checksum: String) = api.upload("/products/import", api.adminToken(), file, mapOf("checksum" to checksum))

	private fun count(codes: List<String>): Int =
		codes.sumOf { api.post("/products/query", api.adminToken(), mapOf("filters" to mapOf("code" to it))).json.get("total").asInt() }

	private fun ApiResponse.int(field: String) = json.get(field).asInt()

	@Test
	fun `the dry run counts the accepted rows, names the rejected ones with their reason, checks the control total, and writes nothing`() {
		val (file, codes) = fixture()
		val report = dryRun(file)
		assertEquals(200, report.status, report.body)
		assertEquals(3, report.int("accepted"))
		assertEquals(3, report.int("newProducts"))
		assertEquals(0, report.int("existingProducts"))
		val rejected = report.json.get("rejected").list
		assertEquals(listOf("code", "code"), rejected.map { it.get("field").asString() })
		assertEquals(listOf("FIELD_REQUIRED", "FIELD_REQUIRED"), rejected.map { it.get("code").asString() })
		assertEquals("Este campo es obligatorio.", rejected[0].get("message").asString())
		assertEquals(60, report.int("controlTotal"))
		assertEquals(60, report.int("stockTotal"))
		assertTrue(report.json.get("controlMatches").asBoolean())
		assertEquals(64, report.text("checksum").length)
		assertEquals(0, count(codes))
	}

	@Test
	fun `the confirmation writes the reviewed file once, and running it again writes nothing new`() {
		val (file, codes) = fixture()
		val checksum = dryRun(file).text("checksum")
		val first = confirm(file, checksum)
		assertEquals(200, first.status, first.body)
		assertEquals(3, first.int("created"))
		assertEquals(3, count(codes))

		val product = api.post("/products/query", api.adminToken(), mapOf("filters" to mapOf("code" to codes[0]))).json.get("data").get(0)
		assertEquals("ABRAZADERA DE ARRANQUE 1-5/8\"", product.get("name").asString())
		assertEquals(codes[0], product.get("barcode").asString())
		assertEquals("PIECE", product.get("unit").get("code").asString())
		assertTrue(product.get("legacyId").asInt() >= 2_000_000)

		val again = confirm(file, checksum)
		assertEquals(200, again.status, again.body)
		assertEquals(0, again.int("created"))
		assertEquals(3, again.int("existingProducts"))
		assertEquals(3, count(codes))
	}

	@Test
	fun `a file that is not the reviewed one is refused, and nothing is written`() {
		val (reviewed, _) = fixture()
		val checksum = dryRun(reviewed).text("checksum")
		val (changed, codes) = fixture()
		val refused = confirm(changed, checksum)
		assertEquals(409, refused.status)
		assertEquals("IMPORT_FILE_CHANGED", refused.code)
		assertEquals(0, count(codes))
	}

	@Test
	fun `a file whose stock does not add up to its control total is refused`() {
		val prefix = "T" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
		val id = Random.nextInt(2_000_000, 2_000_000_000)
		val file = Xlsx.build(listOf(Xlsx.HEADER, Xlsx.product(id, "${prefix}0", "Caja", stock = 5)) + Xlsx.control(6))
		val report = dryRun(file)
		assertEquals(false, report.json.get("controlMatches").asBoolean())
		assertEquals("IMPORT_CONTROL_MISMATCH", confirm(file, report.text("checksum")).code)
		assertEquals(0, count(listOf("${prefix}0")))
	}

	@Test
	fun `only products manage imports, and a file that is not a spreadsheet is refused`() {
		val office = api.newUser("OFFICE")
		assertEquals("INSUFFICIENT_PERMISSIONS", dryRun(fixture().first, office.token).code)
		assertEquals("IMPORT_FILE_INVALID", dryRun("hola".toByteArray()).code)
	}
}
