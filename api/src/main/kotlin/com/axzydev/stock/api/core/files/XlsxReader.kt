package com.axzydev.stock.api.core.files

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

// One row of a sheet: its number as Excel shows it, and the text of each cell by column letter.
data class SheetRow(val number: Int, val cells: Map<String, String>) {
	fun text(column: String): String? = cells[column]?.trim()?.ifEmpty { null }

	val blank: Boolean get() = cells.values.all { it.isBlank() }
}

// Reads the first sheet of an .xlsx file as rows of text, with the JDK only: the file is a zip of XML parts.
// It reads the values of the cells, not their formats or formulas. A file it cannot read is IMPORT_FILE_INVALID.
// Uploaded files are untrusted: DTDs and external entities are refused, and each part and the row count have a limit.
object XlsxReader {

	private const val MAX_PART_BYTES = 64L * 1024 * 1024
	private const val MAX_ROWS = 50_000
	private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

	private val xml: XMLInputFactory = XMLInputFactory.newFactory().apply {
		setProperty(XMLInputFactory.SUPPORT_DTD, false)
		setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
		setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
	}

	fun read(bytes: ByteArray): List<SheetRow> =
		try {
			val parts = unzip(bytes)
			val sheet = parts[firstSheet(parts)] ?: invalid()
			rows(sheet, parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty())
		} catch (e: ApiException) {
			throw e
		} catch (e: Exception) {
			invalid()
		}

	private fun invalid(): Nothing = throw ApiException(ErrorCode.IMPORT_FILE_INVALID)

	// The parts of the workbook that hold data, each read up to the limit.
	private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
		val parts = mutableMapOf<String, ByteArray>()
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				if (entry.isDirectory || !entry.name.startsWith("xl/") || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
				val out = ByteArrayOutputStream()
				val buffer = ByteArray(8192)
				var total = 0L
				while (true) {
					val read = zip.read(buffer)
					if (read < 0) break
					total += read
					if (total > MAX_PART_BYTES) invalid()
					out.write(buffer, 0, read)
				}
				parts[entry.name] = out.toByteArray()
			}
		}
		if ("xl/workbook.xml" !in parts) invalid()
		return parts
	}

	// The path of the workbook's first sheet, through the workbook's relationships.
	private fun firstSheet(parts: Map<String, ByteArray>): String {
		var relationId: String? = null
		parse(parts.getValue("xl/workbook.xml")) { reader ->
			if (relationId == null && reader.localName == "sheet") relationId = reader.getAttributeValue(RELATIONSHIPS, "id")
		}
		var target: String? = null
		parts["xl/_rels/workbook.xml.rels"]?.let { rels ->
			parse(rels) { reader ->
				if (reader.localName == "Relationship" && reader.getAttributeValue(null, "Id") == relationId) target = reader.getAttributeValue(null, "Target")
			}
		}
		val path = target ?: return "xl/worksheets/sheet1.xml"
		return if (path.startsWith("/")) path.removePrefix("/") else "xl/$path"
	}

	// The shared strings: the text of each <si>, with every run of rich text joined. Phonetic hints are left out.
	private fun sharedStrings(bytes: ByteArray): List<String> {
		val strings = mutableListOf<String>()
		val reader = xml.createXMLStreamReader(ByteArrayInputStream(bytes))
		var current: StringBuilder? = null
		var phonetic = 0
		var inText = false
		while (reader.hasNext()) {
			when (reader.next()) {
				XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
					"si" -> current = StringBuilder()
					"rPh" -> phonetic++
					"t" -> inText = phonetic == 0
				}
				XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inText) current?.append(reader.text)
				XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
					"si" -> strings += current.toString()
					"rPh" -> phonetic--
					"t" -> inText = false
				}
			}
		}
		return strings
	}

	private fun rows(bytes: ByteArray, shared: List<String>): List<SheetRow> {
		val rows = mutableListOf<SheetRow>()
		val reader = xml.createXMLStreamReader(ByteArrayInputStream(bytes))
		var number = 0
		var cells = mutableMapOf<String, String>()
		var column = ""
		var type: String? = null
		var value: StringBuilder? = null
		var inValue = false
		var nextColumn = 0
		while (reader.hasNext()) {
			when (reader.next()) {
				XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
					"row" -> {
						number = reader.getAttributeValue(null, "r")?.toIntOrNull() ?: (number + 1)
						cells = mutableMapOf()
						nextColumn = 0
					}
					"c" -> {
						// A cell names its column; one that does not follows the previous one.
						column = reader.getAttributeValue(null, "r")?.takeWhile { it.isLetter() } ?: letters(nextColumn)
						nextColumn = index(column) + 1
						type = reader.getAttributeValue(null, "t")
						value = StringBuilder()
					}
					"v", "t" -> inValue = true
				}
				XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inValue) value?.append(reader.text)
				XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
					"v", "t" -> inValue = false
					"c" -> {
						val raw = value.toString()
						cells[column] = if (type == "s") shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: invalid() else raw
						value = null
					}
					"row" -> {
						rows += SheetRow(number, cells)
						if (rows.size > MAX_ROWS) invalid()
					}
				}
			}
		}
		return rows
	}

	private fun parse(bytes: ByteArray, onStart: (XMLStreamReader) -> Unit) {
		val reader = xml.createXMLStreamReader(ByteArrayInputStream(bytes))
		while (reader.hasNext()) if (reader.next() == XMLStreamConstants.START_ELEMENT) onStart(reader)
	}

	// Column letters and their zero-based index: A is 0, Z is 25, AA is 26.
	private fun index(letters: String): Int = letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

	private fun letters(index: Int): String {
		var n = index + 1
		val out = StringBuilder()
		while (n > 0) {
			val rest = (n - 1) % 26
			out.insert(0, 'A' + rest)
			n = (n - 1) / 26
		}
		return out.toString()
	}
}
