package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.files.XlsxReader
import com.axzydev.stock.api.support.Xlsx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

// F2-07 · unit: reading the legacy file. PZA is PIECE, rows without a code are skipped and reported,
// every rejected row says why, and the control total is read from the CONTPAQ note.
class LegacyProductFileTest {

	private fun parse(rows: List<Map<String, Any?>>) = LegacyProductFile.parse(XlsxReader.read(Xlsx.build(listOf(Xlsx.HEADER) + rows)))

	@Test
	fun `the products are read, named as written, with PZA as PIECE, and the control total from the note`() {
		val file = parse(
			listOf(
				Xlsx.product(1, "ABRAA1B", "ABRAZADERA DE ARRANQUE 1-5/8\"", stock = 10),
				Xlsx.product(2, "VSINFIN5161", "VARILLA  SINFIN GALV. 5/16\" X 1 MT", stock = 5),
				mapOf(),
			) + Xlsx.control(15),
		)
		assertEquals(listOf("ABRAA1B", "VSINFIN5161"), file.products.map { it.code })
		assertEquals("VARILLA SINFIN GALV. 5/16\" X 1 MT", file.products[1].name)
		assertEquals(listOf("PIECE", "PIECE"), file.products.map { it.unit })
		assertEquals(listOf(1, 2), file.products.map { it.legacyId })
		assertEquals(BigDecimal(15), file.stockTotal)
		assertEquals(15L, file.controlTotal)
		// The note's two rows have no code: they are reported, and the empty row is not.
		assertEquals(listOf(5 to "code", 6 to "code"), file.rejected.map { it.row to it.field })
		assertEquals(setOf(ErrorCode.FIELD_REQUIRED), file.rejected.map { it.code }.toSet())
	}

	@Test
	fun `each rejected row says which field failed and why`() {
		val file = parse(
			listOf(
				Xlsx.product(1, "A B", "Nombre"),
				Xlsx.product(2, "OK0001", "X"),
				Xlsx.product(3, "OK0002", "Caja", unit = "KG"),
				Xlsx.product(4, "OK0003", "Caja", stock = -1),
				Xlsx.product(5, "OK0004", "Caja"),
				Xlsx.product(6, "OK0004", "Caja otra vez"),
				Xlsx.product(5, "OK0005", "Caja con id repetido"),
				Xlsx.product(7, "7501031311308", "Barras con dígito equivocado"),
			),
		)
		assertEquals(listOf("OK0004"), file.products.map { it.code })
		assertEquals(
			listOf(
				Triple(2, "code", ErrorCode.FORMAT_INVALID),
				Triple(3, "name", ErrorCode.FORMAT_INVALID),
				Triple(4, "unit", ErrorCode.CATALOG_VALUE_INVALID),
				Triple(5, "stock", ErrorCode.QUANTITY_INVALID),
				Triple(7, "code", ErrorCode.DUPLICATE_VALUE),
				Triple(8, "legacyId", ErrorCode.DUPLICATE_VALUE),
				Triple(9, "barcode", ErrorCode.BARCODE_INVALID),
			),
			file.rejected.map { Triple(it.row, it.field, it.code) },
		)
		assertNull(file.controlTotal)
	}

	@Test
	fun `a file without the legacy columns is not the legacy file`() {
		val other = XlsxReader.read(Xlsx.build(listOf(mapOf("A" to "CODIGO", "B" to "NOMBRE"), mapOf("A" to "X1", "B" to "Algo"))))
		assertEquals(ErrorCode.IMPORT_FILE_INVALID, assertThrows<ApiException> { LegacyProductFile.parse(other) }.code)
		assertEquals(ErrorCode.IMPORT_FILE_INVALID, assertThrows<ApiException> { XlsxReader.read("not a spreadsheet".toByteArray()) }.code)
	}
}
