package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.core.errors.ErrorCode

// The rules of a label template (labels.md sections 3 and 4). A template is ZPL that reaches a printer, so only an
// allowed set of commands passes, and data reaches it only through the sanitizer.
object ZplRules {

	data class Problem(val code: ErrorCode, val token: String?)

	private val KEY = Regex("^[A-Z][A-Z0-9_]{1,59}$")
	private val PLACEHOLDER = Regex("\\{\\{([^{}]*)\\}\\}")

	const val COPIES = "copies"

	// The placeholders of each key, besides {{copies}}. A new key has only {{copies}} until its data is defined.
	val PLACEHOLDERS: Map<String, Set<String>> = mapOf(
		"PRODUCT" to setOf("code", "name", "barcode", "unit"),
		"LOCATION" to setOf("code", "warehouse_code", "aisle", "rack", "level"),
	)

	// Layout, text, barcodes and quantity (labels.md section 4, rule 2). ^A takes the font right after it.
	private val ALLOWED = setOf("XA", "XZ", "PW", "LL", "LH", "FO", "GB", "A", "CF", "FB", "FD", "FS", "BY", "BC", "BQ", "PQ")

	private const val MAX_VALUE = 60

	fun key(value: String): ErrorCode? = if (KEY.matches(value)) null else ErrorCode.FORMAT_INVALID

	// Every problem of a template: not one label from ^XA to ^XZ; a command outside the list (every ~ command is);
	// a placeholder its key does not have.
	fun check(key: String, zpl: String): List<Problem> {
		val problems = mutableListOf<Problem>()
		val commands = commands(zpl)
		val text = zpl.trim()
		val oneLabel = commands.count { it == "^XA" } == 1 && commands.count { it == "^XZ" } == 1 && text.startsWith("^XA") && text.endsWith("^XZ")
		if (!oneLabel) problems += Problem(ErrorCode.ZPL_BLOCK_INVALID, null)
		commands.filter { it.startsWith("~") || code(it) !in ALLOWED }.distinct().forEach { problems += Problem(ErrorCode.ZPL_COMMAND_NOT_ALLOWED, it) }
		val allowed = PLACEHOLDERS[key].orEmpty() + COPIES
		PLACEHOLDER.findAll(zpl).map { it.groupValues[1] }.filter { it !in allowed }.distinct().forEach { problems += Problem(ErrorCode.ZPL_PLACEHOLDER_UNKNOWN, it) }
		return problems
	}

	// What a value may carry into ZPL: no command prefixes (^, ~) and no escape (\), and at most 60 characters.
	fun sanitize(value: String): String = value.filterNot { it == '^' || it == '~' || it == '\\' }.take(MAX_VALUE)

	fun copies(value: Int): ErrorCode? = if (value in 1..100) null else ErrorCode.FORMAT_INVALID

	// The template with every placeholder filled by its sanitized value. Copies go in {{copies}}; a template without it
	// gets ^PQ before its ^XZ, so the copies asked for are always the copies printed.
	fun fill(zpl: String, values: Map<String, String>, copies: Int): String {
		val filled = PLACEHOLDER.replace(zpl) { match ->
			val name = match.groupValues[1]
			if (name == COPIES) copies.toString() else sanitize(values[name].orEmpty())
		}
		if (PLACEHOLDER.findAll(zpl).any { it.groupValues[1] == COPIES }) return filled
		val end = filled.lastIndexOf("^XZ")
		return filled.substring(0, end) + "^PQ$copies" + filled.substring(end)
	}

	// The commands of a ZPL text: each ^ or ~ with the two characters after it.
	private fun commands(zpl: String): List<String> =
		zpl.indices.filter { zpl[it] == '^' || zpl[it] == '~' }.map { zpl.substring(it, minOf(it + 3, zpl.length)) }

	private fun code(command: String): String = if (command.length > 1 && command[1] == 'A') "A" else command.drop(1)
}
