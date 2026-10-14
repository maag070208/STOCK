package com.axzydev.stock.data

// Exact arithmetic on the decimal texts the API writes, like "1234.500000". The app shows a quantity as it came, but
// placement is the one place where it must add them up: the operator splits what was counted across locations and the
// sum has to be the counted quantity (docs/modules/material-receiving.md section 7). A quantity is never read as a
// floating point number, which would round the digits; it is read as a whole number of its smallest digit instead.
object Decimals {

	// Whether the value is a non-negative decimal, the way the API's own quantity rules read it. A negative or a
	// letter is not a quantity the app holds.
	fun isValid(value: String): Boolean = DECIMAL.matches(value) && units(value, scaleOf(value)) != null

	// The sum of the values, at the largest scale of them. Null when a value is not a decimal, or is too large to hold.
	fun sum(values: List<String>): String? {
		var total = "0"
		for (value in values) total = add(total, value) ?: return null
		return total
	}

	// a + b, at the larger scale. Null when a value is not a decimal, or the sum is too large to hold.
	fun add(a: String, b: String): String? {
		val scale = maxOf(scaleOf(a), scaleOf(b))
		val left = units(a, scale) ?: return null
		val right = units(b, scale) ?: return null
		// The two are added far below the limit only while each is far below it; anything larger is not dealt with here.
		val sum = left + right
		if (sum < 0 || sum < left) return null
		return render(sum, scale)
	}

	// a − b, at the larger scale. Null when a value is not a decimal, or b is above a: a negative quantity is not a
	// placement either.
	fun subtract(a: String, b: String): String? {
		val scale = maxOf(scaleOf(a), scaleOf(b))
		val left = units(a, scale) ?: return null
		val right = units(b, scale) ?: return null
		if (right > left) return null
		return render(left - right, scale)
	}

	// Whether two texts are the same quantity, whatever digits each carries: "500", "500.0" and "500.000000" are the same.
	fun same(a: String, b: String): Boolean {
		val scale = maxOf(scaleOf(a), scaleOf(b))
		val left = units(a, scale) ?: return false
		val right = units(b, scale) ?: return false
		return left == right
	}

	// Whether a is above b, whatever digits each carries. Something that is not a quantity is above nothing.
	fun above(a: String, b: String): Boolean {
		val scale = maxOf(scaleOf(a), scaleOf(b))
		val left = units(a, scale) ?: return false
		val right = units(b, scale) ?: return false
		return left > right
	}

	private val DECIMAL = Regex("^[0-9]+(\\.[0-9]+)?$")

	private fun scaleOf(value: String): Int = value.substringAfter('.', "").length

	// The value as a whole number of its smallest digit: "1234.5" at scale 3 is 1234500.
	private fun units(value: String, scale: Int): Long? {
		if (!DECIMAL.matches(value) || scale > 18) return null
		val whole = value.substringBefore('.')
		val fraction = value.substringAfter('.', "").padEnd(scale, '0')
		return (whole + fraction).toLongOrNull()
	}

	// A whole number of the smallest digit as a decimal text: 1234500 at scale 3 is "1234.500".
	private fun render(units: Long, scale: Int): String {
		if (scale == 0) return units.toString()
		val digits = units.toString().padStart(scale + 1, '0')
		return digits.dropLast(scale) + "." + digits.takeLast(scale)
	}
}
