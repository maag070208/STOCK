package com.axzydev.stock.api.core.files

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import java.io.ByteArrayOutputStream

// Draws a table as a PDF: a title, its headings, its rows and its totals, the shape every report export has. The standard
// font covers Latin-1; anything else becomes "?", so no file fails to be produced over a name it cannot print.
object PdfTableWriter {

	private const val MARGIN = 40f
	private const val ROW_HEIGHT = 14f
	private const val HEADING_SIZE = 9f
	private const val BODY_SIZE = 8f
	private const val TITLE_SIZE = 14f

	fun write(title: String, columns: List<String>, rows: List<List<String>>, totals: List<String> = emptyList()): ByteArray {
		PDDocument().use { document ->
			val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
			val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
			val page = PDPage(PDRectangle.LETTER)
			document.addPage(page)
			var content = PDPageContentStream(document, page)
			var y = page.mediaBox.height - MARGIN
			var open = true

			fun line(cells: List<String>, font: PDFont, size: Float, gap: Float) {
				if (!open) return
				val width = (page.mediaBox.width - 2 * MARGIN) / maxOf(cells.size, 1)
				cells.forEachIndexed { index, value ->
					content.beginText()
					content.setFont(font, size)
					content.newLineAtOffset(MARGIN + index * width, y)
					content.showText(printable(value, size * 0.55f, width))
					content.endText()
				}
				y -= gap
				if (y < MARGIN + ROW_HEIGHT) {
					content.close()
					val next = PDPage(page.mediaBox)
					document.addPage(next)
					content = PDPageContentStream(document, next)
					y = next.mediaBox.height - MARGIN
					open = true
				}
			}

			line(listOf(title), bold, TITLE_SIZE, 24f)
			line(columns, bold, HEADING_SIZE, ROW_HEIGHT)
			rows.forEach { line(it, regular, BODY_SIZE, ROW_HEIGHT) }
			if (totals.isNotEmpty()) line(totals, bold, HEADING_SIZE, ROW_HEIGHT)
			if (open) content.close()

			val out = ByteArrayOutputStream()
			document.save(out)
			return out.toByteArray()
		}
	}

	// A character the standard font cannot print becomes "?", and a value wider than its column is cut with an ellipsis.
	private fun printable(value: String, size: Float, width: Float): String {
		val clean = value.map { if (WinAnsiEncoding.INSTANCE.contains(it.code)) it else '?' }.joinToString("")
		var text = clean
		while (text.length > 1 && text.length * size > width - 4) text = text.dropLast(2)
		return if (text.length < clean.length) "$text…" else text
	}
}
