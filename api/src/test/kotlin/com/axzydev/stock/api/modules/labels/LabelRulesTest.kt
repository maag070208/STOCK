package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.Rules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F3-01 to F3-07 · unit: the key of a template, the ZPL it may hold, the placeholders of each key, the sanitizing of
// printed values, the copies, and the printers' addresses.
class LabelRulesTest {

	private val product = """
		^XA
		^PW812
		^LL406
		^LH0,0
		^CF0,40
		^FO30,30^FB752,2,0,L,0^FD{{name}}^FS
		^FO30,150^BY3,2,110^BCN,110,Y,N,N^FD{{barcode}}^FS
		^FO30,320^A0N,40,40^FD{{code}}  {{unit}}^FS
		^PQ{{copies}}
		^XZ
	""".trimIndent()

	private fun codes(key: String, zpl: String) = ZplRules.check(key, zpl).map { it.code to it.token }

	@Test
	fun `a template key is UPPER_SNAKE_CASE`() {
		assertNull(ZplRules.key("PRODUCT"))
		assertNull(ZplRules.key("ORDER_LABEL"))
		assertEquals(ErrorCode.FORMAT_INVALID, ZplRules.key("product"))
		assertEquals(ErrorCode.FORMAT_INVALID, ZplRules.key("PRODUCT LABEL"))
	}

	@Test
	fun `the example product label passes`() {
		assertEquals(emptyList<Any>(), codes("PRODUCT", product))
	}

	@Test
	fun `a template is one label, from XA to XZ`() {
		assertEquals(listOf(ErrorCode.ZPL_BLOCK_INVALID to null), codes("PRODUCT", product.removeSuffix("^XZ")))
		assertEquals(listOf(ErrorCode.ZPL_BLOCK_INVALID to null), codes("PRODUCT", "$product\n^XA^FDotra^FS^XZ"))
		assertEquals(listOf(ErrorCode.ZPL_BLOCK_INVALID to null), codes("PRODUCT", "^FO1,1^FDantes^FS\n$product"))
	}

	@Test
	fun `only the allowed commands pass, and the ones that change the printer never do`() {
		for (command in listOf("~JR", "^DF", "^XF", "^ID", "^JU", "^JR", "^HH", "~HS")) {
			val found = codes("PRODUCT", product.replace("^PQ{{copies}}", "^PQ{{copies}}\n$command"))
			assertEquals(listOf(ErrorCode.ZPL_COMMAND_NOT_ALLOWED to command), found, command)
		}
		// ^A takes the font name right after it: ^A0 is the command ^A.
		assertEquals(emptyList<Any>(), codes("PRODUCT", product.replace("^A0N", "^ADN")))
	}

	@Test
	fun `a template prints the variables it names, and only a name that is not one is refused`() {
		// The template decides what it prints: a name the API does not know is allowed (the screen offers the known ones
		// as suggestions, and the value is empty until something fills it), and a name that is not a name is not.
		assertEquals(emptyList<Any>(), codes("PRODUCT", product.replace("{{unit}}", "{{rack}}")))
		assertEquals(emptyList<Any>(), codes("PRODUCT", product.replace("{{unit}}", "{{precio_oferta}}")))
		assertEquals(listOf(ErrorCode.ZPL_PLACEHOLDER_UNKNOWN to "precio oferta"), codes("PRODUCT", product.replace("{{unit}}", "{{precio oferta}}")))
		assertEquals(listOf(ErrorCode.ZPL_PLACEHOLDER_UNKNOWN to "9precio"), codes("PRODUCT", product.replace("{{unit}}", "{{9precio}}")))
		val location = "^XA^FO30,20^A0N,200,180^FD{{code}}^FS^FO430,40^A0N,40,40^FD{{warehouse_code}} {{aisle}} {{rack}} {{level}}^FS^PQ{{copies}}^XZ"
		assertEquals(emptyList<Any>(), codes("LOCATION", location))
		assertEquals(emptyList<Any>(), codes("NEW_LABEL", "^XA^FDfijo^FS^PQ{{copies}}^XZ"))
	}

	@Test
	fun `the variables a template uses are the ones a screen offers`() {
		assertEquals(listOf("aisle", "code", "copies", "level", "rack", "warehouse_code"), ZplRules.used("^XA^FD{{code}} {{copies}}^FS^FO30,30^FD{{aisle}}{{rack}}{{level}}{{warehouse_code}}^FS^XZ"))
	}

	@Test
	fun `a printed value loses the carets, tildes and backslashes, and is cut to 60 characters`() {
		assertEquals("TUBO  1-7/8\" X 6 MT", ZplRules.sanitize("TUBO ^~ 1-7/8\" X 6 MT"))
		assertEquals("ab", ZplRules.sanitize("a\\b"))
		assertEquals(60, ZplRules.sanitize("A".repeat(80)).length)
	}

	@Test
	fun `copies are 1 to 100, and fill PQ, or are added when the template has no copies`() {
		assertNull(ZplRules.copies(1))
		assertNull(ZplRules.copies(100))
		assertEquals(ErrorCode.FORMAT_INVALID, ZplRules.copies(0))
		assertEquals(ErrorCode.FORMAT_INVALID, ZplRules.copies(101))
		val filled = ZplRules.fill(product, mapOf("code" to "ABRAA1B", "name" to "ABRAZADERA", "barcode" to "ABRAA1B", "unit" to "Pieza"), copies = 3)
		assertTrue(filled.contains("^PQ3"), filled)
		assertTrue(filled.contains("^FDABRAZADERA^FS"), filled)
		assertTrue(ZplRules.fill("^XA^FDfijo^FS^XZ", emptyMap(), copies = 2).endsWith("^PQ2^XZ"))
	}

	@Test
	fun `a printer's address is four numbers from 0 to 255, without leading zeros`() {
		assertNull(Rules.ipAddress("192.168.1.50"))
		assertNull(Rules.ipAddress("10.0.0.1"))
		for (wrong in listOf("192.168.1", "256.1.1.1", "192.168.01.5", "192.168.1.5 ", "printer.local", "1.2.3.4.5")) {
			assertEquals(ErrorCode.IP_ADDRESS_INVALID, Rules.ipAddress(wrong), wrong)
		}
	}

	// F3-10 · unit: the seed holds the product and location labels, and both pass the template rules.
	@Test
	fun `the seed templates are PRODUCT and LOCATION, and pass the rules`() {
		assertEquals(listOf("PRODUCT", "LOCATION"), SeedLabels.all.map { it.key })
		SeedLabels.all.forEach { assertEquals(emptyList<Any>(), codes(it.key, it.zpl), it.key) }
	}
}
