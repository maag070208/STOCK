package com.axzydev.stock.api.modules.catalogs

import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.validation.FieldError

// The rules of docs/rules/catalogs.md that need no database.
object CatalogRules {

	val CODE = Regex("^[A-Z][A-Z0-9_]{0,59}$")
	val CATALOG_CODE = Regex("^[A-Z][A-Z0-9_]{1,59}$")

	// Every active item needs a label in each of these.
	val LOCALES = listOf("es", "en")

	// The vocabulary of attributes. Developers define the keys, because the code reads them. Administrators set the values.
	private val ATTRIBUTES: Map<String, (Any?) -> Boolean> = mapOf(
		// No transition is allowed out of this value.
		"terminal" to { value -> value is Boolean },
		// How many decimals a quantity in this unit of measure may have.
		"decimal_places" to { value -> value is Int && value in 0..6 },
		// The COUNTRY a STATE belongs to, by its code: an address takes a state of its own country.
		"country" to { value -> value is String && CODE.matches(value) },
		// Whether a MOVEMENT_TYPE adds to a balance ("in") or takes from it ("out").
		"direction" to { value -> value == "in" || value == "out" },
	)

	// An item cannot be active without its Spanish and its English label.
	fun activation(active: Boolean, labels: Set<String>): List<FieldError> =
		if (!active) emptyList() else LOCALES.filter { it !in labels }.map { FieldError("labels.$it", ErrorCode.FIELD_REQUIRED) }

	fun labels(labels: Map<String, LabelBody>): List<FieldError> = labels.flatMap { (locale, body) ->
		val text = body.label?.trim().orEmpty()
		val description = body.description?.trim().orEmpty()
		buildList {
			if (locale !in LOCALES) add(FieldError("labels.$locale", ErrorCode.FORMAT_INVALID))
			if (text.isEmpty()) add(FieldError("labels.$locale.label", ErrorCode.FIELD_REQUIRED))
			if (text.length > 120) add(FieldError("labels.$locale.label", ErrorCode.FIELD_TOO_LONG))
			if (description.length > 500) add(FieldError("labels.$locale.description", ErrorCode.FIELD_TOO_LONG))
		}
	}

	// A key outside the vocabulary is an unknown field. A value of the wrong type is a format error.
	fun attributes(attributes: Map<String, Any?>): List<FieldError> = attributes.mapNotNull { (key, value) ->
		val accepts = ATTRIBUTES[key] ?: return@mapNotNull FieldError("attributes.$key", ErrorCode.UNKNOWN_FIELD)
		if (accepts(value)) null else FieldError("attributes.$key", ErrorCode.FORMAT_INVALID)
	}
}
