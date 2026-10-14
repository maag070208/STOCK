package com.axzydev.stock.api.core.validation

import com.axzydev.stock.api.core.errors.ErrorCode

class FieldError(val field: String, val code: ErrorCode)

// Every field that failed, each with its code (validation.md principle 6).
class ValidationException(val errors: List<FieldError>) : RuntimeException(errors.joinToString { "${it.field}:${it.code}" })

// The rules of validation.md section 3, one function per field type. Each gives the error code, or null when the value passes.
object Rules {

	private val CONTROL = Regex("\\p{Cntrl}")
	private val USERNAME = Regex("^[a-z0-9._-]{3,60}$")
	private val EMAIL_LOCAL = Regex("^[A-Za-z0-9._%+-]{1,64}$")
	private val EMAIL_LABEL = Regex("^[A-Za-z0-9-]+$")
	private val EMAIL_TOP_LEVEL = Regex("^[A-Za-z]{2,}$")

	fun name(value: String): ErrorCode? =
		when {
			value.length > 120 -> ErrorCode.FIELD_TOO_LONG
			value.length < 2 || CONTROL.containsMatchIn(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	fun username(value: String): ErrorCode? =
		when {
			value.length > 60 -> ErrorCode.FIELD_TOO_LONG
			!USERNAME.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	fun email(value: String): ErrorCode? {
		val parts = value.split("@")
		if (value.length > 254 || parts.size != 2) return ErrorCode.EMAIL_INVALID
		val (local, domain) = parts
		val labels = domain.split(".")
		val valid = EMAIL_LOCAL.matches(local) && !local.startsWith(".") && !local.endsWith(".") &&
			labels.size >= 2 && labels.all { EMAIL_LABEL.matches(it) } && EMAIL_TOP_LEVEL.matches(labels.last())
		return if (valid) null else ErrorCode.EMAIL_INVALID
	}

	fun language(value: String): ErrorCode? = if (value == "es" || value == "en") null else ErrorCode.FORMAT_INVALID

	private val CODE = Regex("^[A-Z0-9-]{2,20}$")
	private val TAX_ID = Regex("^[A-ZÑ&]{3,4}([0-9]{6})[A-Z0-9]{3}$")
	private val PHONE_SEPARATORS = Regex("[ ()-]")
	private val PHONE = Regex("^[0-9]{10}$")
	private val POSTAL_CODE = Regex("^[0-9]{5}$")
	private val ADDRESS_NUMBER = Regex("^[A-Za-z0-9/-]+$")

	// Codes of products, suppliers, customers and warehouses: uppercase letters, digits and hyphens. Normalized to uppercase first.
	fun code(value: String): ErrorCode? =
		when {
			value.length > 20 -> ErrorCode.FIELD_TOO_LONG
			!CODE.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	// The Mexican RFC: 3 letters for a company or 4 for a person, the date as YYMMDD, and 3 letters or digits.
	// Normalized to uppercase first. The date must be real in either century, so 000229 is the leap day of 2000.
	fun taxId(value: String): ErrorCode? {
		val date = TAX_ID.matchEntire(value)?.groupValues?.get(1) ?: return ErrorCode.TAX_ID_INVALID
		val (year, month, day) = date.chunked(2).map(String::toInt)
		val real = listOf(1900, 2000).any { century -> runCatching { java.time.LocalDate.of(century + year, month, day) }.isSuccess }
		return if (real) null else ErrorCode.TAX_ID_INVALID
	}

	// A phone is stored as 10 digits: spaces, dashes, parentheses and a leading +52 are removed. Anything else stays and fails.
	fun normalizePhone(value: String): String = value.trim().removePrefix("+52").replace(PHONE_SEPARATORS, "")

	fun phone(value: String): ErrorCode? = if (PHONE.matches(value)) null else ErrorCode.PHONE_INVALID

	fun postalCode(value: String): ErrorCode? = if (POSTAL_CODE.matches(value)) null else ErrorCode.POSTAL_CODE_INVALID

	private val OCTET = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])"
	private val IP_ADDRESS = Regex("^$OCTET(\\.$OCTET){3}$")

	// An IPv4 address: four numbers from 0 to 255, without leading zeros or spaces (validation.md, IP address).
	fun ipAddress(value: String): ErrorCode? = if (IP_ADDRESS.matches(value)) null else ErrorCode.IP_ADDRESS_INVALID

	// Exterior and interior numbers: letters, digits, slashes and hyphens, up to 10. S/N (no number) passes.
	fun addressNumber(value: String): ErrorCode? =
		when {
			value.length > 10 -> ErrorCode.FIELD_TOO_LONG
			!ADDRESS_NUMBER.matches(value) -> ErrorCode.FORMAT_INVALID
			else -> null
		}

	fun text(max: Int): (String) -> ErrorCode? = { value ->
		if (value.length > max) ErrorCode.FIELD_TOO_LONG else if (CONTROL.containsMatchIn(value)) ErrorCode.FORMAT_INVALID else null
	}
}

// Collects the errors of one request, then fails with all of them. Text is trimmed before its rule runs.
class Validator {
	private val errors = mutableListOf<FieldError>()

	fun required(field: String, value: String?, rule: (String) -> ErrorCode?, normalize: (String) -> String = { it }): String {
		val clean = value?.trim()?.let(normalize)
		if (clean.isNullOrEmpty()) {
			errors += FieldError(field, ErrorCode.FIELD_REQUIRED)
			return ""
		}
		rule(clean)?.let { errors += FieldError(field, it) }
		return clean
	}

	// An absent or empty optional field is null.
	fun optional(field: String, value: String?, rule: (String) -> ErrorCode?, normalize: (String) -> String = { it }): String? {
		val clean = value?.trim()?.let(normalize)
		if (clean.isNullOrEmpty()) return null
		rule(clean)?.let { errors += FieldError(field, it) }
		return clean
	}

	fun reject(field: String, code: ErrorCode) {
		errors += FieldError(field, code)
	}

	fun check() {
		if (errors.isNotEmpty()) throw ValidationException(errors.toList())
	}
}

// The body of a PATCH: only the fields that were sent change. A field outside `allowed` is rejected.
class Patch(private val body: Map<String, Any?>, allowed: Set<String>) {

	init {
		val unknown = body.keys - allowed
		if (unknown.isNotEmpty()) throw ValidationException(unknown.sorted().map { FieldError(it, ErrorCode.UNKNOWN_FIELD) })
	}

	fun has(field: String): Boolean = field in body

	fun text(field: String): String? = typed<String>(field)

	fun flag(field: String): Boolean? = typed<Boolean>(field)

	fun number(field: String): Int? = typed<Int>(field)

	private inline fun <reified T> typed(field: String): T? =
		when (val value = body[field]) {
			null -> null
			is T -> value
			else -> throw ValidationException(listOf(FieldError(field, ErrorCode.FORMAT_INVALID)))
		}
}
