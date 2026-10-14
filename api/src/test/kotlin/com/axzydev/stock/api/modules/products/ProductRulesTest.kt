package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.errors.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// F2-06 · unit: the product code, the name and its normalization, and the barcode with its check digit.
class ProductRulesTest {

	@Test
	fun `a name is trimmed and its doubled spaces collapsed, and its case is kept`() {
		assertEquals("ABRAZADERA DE Arranque 1-5/8\"", ProductRules.normalizeName("  ABRAZADERA   DE  Arranque 1-5/8\"  "))
	}

	@Test
	fun `a name takes letters with accents, digits, spaces and the signs of the legacy names`() {
		assertNull(ProductRules.name("VARILLA SINFIN GALV. 5/16\" X 1 MT"))
		assertNull(ProductRules.name("CODO 90° (PVC) & TEE, 'A'"))
		assertNull(ProductRules.name("AÑO NIÑO ÜBER"))
		assertNull(ProductRules.name("ADORNO MALEABLE #10 HOJA IZQ."))
		assertNull(ProductRules.name("LAM. PERF. 4¨ X 8¨ C-24"))
		assertEquals(ErrorCode.FORMAT_INVALID, ProductRules.name("TUBO @4"))
		assertEquals(ErrorCode.FORMAT_INVALID, ProductRules.name("TUBO\t4"))
		assertEquals(ErrorCode.FORMAT_INVALID, ProductRules.name("T"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, ProductRules.name("A".repeat(121)))
	}

	@Test
	fun `a product code is uppercase letters, digits and hyphens, 2 to 20 characters`() {
		assertNull(ProductRules.code("ABRAA1B"))
		assertNull(ProductRules.code("VSINFIN5161"))
		assertNull(ProductRules.code("NEW-01"))
		assertEquals(ErrorCode.FORMAT_INVALID, ProductRules.code("A"))
		assertEquals(ErrorCode.FORMAT_INVALID, ProductRules.code("AB C"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, ProductRules.code("A".repeat(21)))
	}

	@Test
	fun `a barcode is 6 to 20 uppercase letters, digits and hyphens`() {
		assertNull(ProductRules.barcode("ABRAA1B"))
		assertEquals(ErrorCode.BARCODE_INVALID, ProductRules.barcode("AB12"))
		assertEquals(ErrorCode.BARCODE_INVALID, ProductRules.barcode("ABC 123"))
		assertEquals(ErrorCode.BARCODE_INVALID, ProductRules.barcode("A".repeat(21)))
	}

	@Test
	fun `a barcode of 13 or 12 digits needs a valid EAN-13 or UPC-A check digit`() {
		assertNull(ProductRules.barcode("7501031311309"))
		assertEquals(ErrorCode.BARCODE_INVALID, ProductRules.barcode("7501031311308"))
		assertNull(ProductRules.barcode("036000291452"))
		assertEquals(ErrorCode.BARCODE_INVALID, ProductRules.barcode("036000291453"))
		// Other lengths of digits are internal codes, with no check digit.
		assertNull(ProductRules.barcode("12345678"))
	}

	@Test
	fun `without a barcode, the product code is the barcode`() {
		assertEquals("ABRAA1B", ProductRules.barcodeOrCode(null, "ABRAA1B"))
		assertEquals("ABRAA1B", ProductRules.barcodeOrCode("  ", "ABRAA1B"))
		assertEquals("7501031311309", ProductRules.barcodeOrCode(" 7501031311309 ", "ABRAA1B"))
	}
}
