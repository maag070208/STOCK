package com.axzydev.stock.api.core.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// F6-05 · unit: the .xlsx the API writes is one the import can read back, numbers included (reports.md section 4).
class XlsxWriterTest {

	@Test
	fun `the sheet it writes is the sheet the reader reads`() {
		val bytes = XlsxWriter.write(
			sheet = "Existencias",
			columns = listOf("Almacén", "Ubicación", "Material", "Cantidad"),
			rows = listOf(
				listOf("CENTRAL", "K05A", "Tornillo & tuerca", "1500"),
				listOf("CENTRAL", "K05B", "Codo <galvanizado>", "2.5"),
			),
			totals = listOf("Total", "", "", "1502.5"),
		)
		val rows = XlsxReader.read(bytes)
		assertEquals(4, rows.size)
		assertEquals(listOf("Almacén", "Ubicación", "Material", "Cantidad"), listOf("A", "B", "C", "D").map { column -> rows[0].cells[column] })
		// The text keeps its signs: the escape of the XML is the reader's business, not the sheet's.
		assertEquals("Tornillo & tuerca", rows[1].text("C"))
		assertEquals("Codo <galvanizado>", rows[2].text("C"))
		// The numbers travel as numbers, and the reader answers them as the text Excel shows.
		assertEquals("1500", rows[1].text("D"))
		assertEquals("2.5", rows[2].text("D"))
		assertEquals("1502.5", rows[3].text("D"))
	}

	@Test
	fun `a sheet with no rows, and a value with nothing in it, are written without breaking the file`() {
		val empty = XlsxWriter.write("Vacío", listOf("A", "B"), emptyList())
		val rows = XlsxReader.read(empty)
		assertEquals(1, rows.size)
		assertEquals(listOf("A", "B"), listOf("A", "B").map { column -> rows[0].cells[column] })

		val holes = XlsxWriter.write("Huecos", listOf("A", "B"), listOf(listOf("", "1")))
		val withHoles = XlsxReader.read(holes)
		assertEquals(2, withHoles.size)
		assertTrue(withHoles[1].cells["A"]!!.isEmpty())
		assertEquals("1", withHoles[1].text("B"))
	}
}
