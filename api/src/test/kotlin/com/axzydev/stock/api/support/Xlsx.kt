package com.axzydev.stock.api.support

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Builds a small spreadsheet in memory, the way Excel stores one: text in shared strings, numbers in the cells.
// Each row is a map from column letter to value. The tests use it for fixtures, so no real file is ever in the repository.
object Xlsx {

	fun build(rows: List<Map<String, Any?>>, sheet: String = "Hoja1"): ByteArray {
		val strings = mutableListOf<String>()
		val index = { text: String -> strings.indexOf(text).takeIf { it >= 0 } ?: strings.size.also { strings += text } }
		val body = rows.mapIndexed { i, row ->
			val number = i + 1
			val cells = row.entries.sortedBy { it.key }.joinToString("") { (column, value) ->
				when (value) {
					null -> """<c r="$column$number"/>"""
					is Number -> """<c r="$column$number"><v>$value</v></c>"""
					else -> """<c r="$column$number" t="s"><v>${index(value.toString())}</v></c>"""
				}
			}
			"""<row r="$number">$cells</row>"""
		}.joinToString("")
		val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
		val sharedXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""" +
			strings.joinToString("") { "<si><t xml:space=\"preserve\">${escape(it)}</t></si>" } + "</sst>"
		val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
			<sheets><sheet name="${escape(sheet)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""
		val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
			<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
			<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
			</Relationships>"""
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use { zip ->
			for ((name, content) in listOf(
				"xl/workbook.xml" to workbook,
				"xl/_rels/workbook.xml.rels" to workbookRels,
				"xl/worksheets/sheet1.xml" to sheetXml,
				"xl/sharedStrings.xml" to sharedXml,
			)) {
				zip.putNextEntry(ZipEntry(name))
				zip.write(content.trimIndent().toByteArray(Charsets.UTF_8))
				zip.closeEntry()
			}
		}
		return out.toByteArray()
	}

	private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

	// The legacy file's columns, in its order (products.md section 2).
	val HEADER = mapOf(
		"A" to "CIDPRODUCTO", "B" to "CCODIGOPRODUCTO", "C" to "CNOMBREPRODUCTO",
		"D" to "CDESCRIPCIONPRODUCTO", "E" to "CUNIDADMEDIDA", "F" to "CEXISTENCIAS",
	)

	fun product(id: Int, code: String?, name: String, unit: String = "PZA", stock: Number = 0): Map<String, Any?> =
		mapOf("A" to id, "B" to code, "C" to name, "D" to name, "E" to unit, "F" to stock)

	// The accounting system's control total, as the legacy file carries it: in column G, below the data.
	fun control(total: Long): List<Map<String, Any?>> = listOf(mapOf("G" to "CONTPAQ"), mapOf("G" to total))
}
