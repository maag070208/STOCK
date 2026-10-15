package com.axzydev.stock.api.core.files

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Writes one sheet as an .xlsx file, the counterpart of XlsxReader: a zip of XML parts, with every cell inline, so no
// shared strings table is needed. A cell that reads as a number is written as one, so Excel adds it up; everything else
// is written as text.
object XlsxWriter {

	private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
	private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
	private const val PACKAGE = "http://schemas.openxmlformats.org/package/2006/relationships"

	private val NUMBER = Regex("^-?\\d+(\\.\\d+)?$")

	// The sheet's name, its headings, its rows, and the totals line the report closes with.
	fun write(sheet: String, columns: List<String>, rows: List<List<String>>, totals: List<String> = emptyList()): ByteArray {
		val every = buildList {
			add(columns)
			addAll(rows)
			if (totals.isNotEmpty()) {
				add(totals)
			}
		}
		val body = every.mapIndexed { index, cells ->
			"""<row r="${index + 1}">""" + cells.mapIndexed { column, value -> cell(reference(column, index + 1), value) }.joinToString("") + "</row>"
		}.joinToString("")
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			part(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
			part(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PACKAGE"><Relationship Id="rId1" Type="$RELATIONSHIPS/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
			part(zip, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="$MAIN" xmlns:r="$RELATIONSHIPS"><sheets><sheet name="${escape(sheet)}" sheetId="1" r:id="rId1"/></sheets></workbook>""")
			part(zip, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PACKAGE"><Relationship Id="rId1" Type="$RELATIONSHIPS/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
			part(zip, "xl/worksheets/sheet1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="$MAIN"><sheetData>$body</sheetData></worksheet>""")
		}
		return out.toByteArray()
	}

	private fun part(zip: ZipOutputStream, name: String, content: String) {
		zip.putNextEntry(ZipEntry(name))
		zip.write(content.toByteArray(Charsets.UTF_8))
		zip.closeEntry()
	}

	private fun cell(reference: String, value: String): String =
		when {
			value.isEmpty() -> """<c r="$reference"/>"""
			// A number is written as the number it is, so Excel sums the column instead of treating it as text.
			NUMBER.matches(value) -> """<c r="$reference"><v>$value</v></c>"""
			else -> """<c r="$reference" t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>"""
		}

	// The column letter of a zero-based position: 0 is A, 26 is AA.
	private fun reference(column: Int, row: Int): String = letters(column) + row

	private fun letters(column: Int): String {
		var value = column
		val text = StringBuilder()
		while (true) {
			text.insert(0, ('A' + value % 26))
			value = value / 26 - 1
			if (value < 0) break
		}
		return text.toString()
	}

	private fun escape(value: String): String = value
		.replace("&", "&amp;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
		.replace("\"", "&quot;")
}
