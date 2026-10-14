package com.axzydev.stock.api.modules.products

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.Rules

// The field rules of a material (products.md section 3, validation.md section 3).
object ProductRules {

	const val UNIT_CATALOG = "UNIT_OF_MEASURE"

	// Letters with their accents, digits, spaces, and the signs the legacy names use: inches, fractions, degrees, sizes (#10),
	// and the diaeresis two names use as an inch mark.
	private val NAME = Regex("^[\\p{L}\\p{Nd} \"/\\-.,()&'°#¨]+$")
	private val BARCODE = Regex("^[A-Z0-9-]{6,20}$")
	private val SPACES = Regex(" {2,}")

	// Trimmed, with doubled spaces collapsed. The case is kept as written.
	fun normalizeName(value: String): String = value.trim().replace(SPACES, " ")

	// A product's code follows the code rule every module shares.
	fun code(value: String): ErrorCode? = Rules.code(value)

	fun name(value: String): ErrorCode? =
		when {
			value.length > 120 -> ErrorCode.FIELD_TOO_LONG
			value.length < 2 || !NAME.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	// Printable in Code 128. A value of 13 or 12 digits is an EAN-13 or a UPC-A, and its check digit must hold.
	fun barcode(value: String): ErrorCode? =
		when {
			!BARCODE.matches(value) -> ErrorCode.BARCODE_INVALID
			(value.length == 12 || value.length == 13) && value.all(Char::isDigit) && !checkDigitHolds(value) -> ErrorCode.BARCODE_INVALID
			else -> null
		}

	// The barcode a product gets: the one given, trimmed and uppercased, or else its code (barcode-scanning.md section 2).
	fun barcodeOrCode(barcode: String?, code: String): String = barcode?.trim()?.uppercase()?.ifEmpty { null } ?: code

	// EAN-13 and UPC-A share the rule: from the right of the body, weights 3 and 1 alternate.
	private fun checkDigitHolds(digits: String): Boolean {
		val sum = digits.dropLast(1).reversed().mapIndexed { index, digit -> (digit - '0') * if (index % 2 == 0) 3 else 1 }.sum()
		return (10 - sum % 10) % 10 == digits.last() - '0'
	}
}
