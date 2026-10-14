package com.axzydev.stock.ui.format

// A quantity as the API wrote it ("1234.500000"), shown with the unit's decimals and the thousands grouped, as the
// web shows it in es-MX: 1,234.50. The digits are cut, not rounded: a stored quantity already has the unit's decimals.
fun formatQuantity(value: String, decimalPlaces: Int?): String {
	val negative = value.startsWith("-")
	val digits = value.removePrefix("-")
	val whole = digits.substringBefore(".").trimStart('0').ifEmpty { "0" }
	val fraction = digits.substringAfter(".", "").padEnd(decimalPlaces ?: 0, '0').take(decimalPlaces ?: 0)
	val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
	val sign = if (negative && (whole != "0" || fraction.any { it != '0' })) "-" else ""
	return sign + grouped + if (fraction.isEmpty()) "" else ".$fraction"
}
