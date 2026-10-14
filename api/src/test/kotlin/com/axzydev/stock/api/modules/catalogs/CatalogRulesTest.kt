package com.axzydev.stock.api.modules.catalogs

import com.axzydev.stock.api.core.errors.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// F1-15 · unit: the catalog rules that need no database.
class CatalogRulesTest {

	private fun pairs(errors: List<com.axzydev.stock.api.core.validation.FieldError>) = errors.map { it.field to it.code }

	@Test
	fun `an item cannot be active without its Spanish and its English label`() {
		assertEquals(emptyList<Any>(), pairs(CatalogRules.activation(active = true, labels = setOf("es", "en"))))
		assertEquals(listOf("labels.en" to ErrorCode.FIELD_REQUIRED), pairs(CatalogRules.activation(active = true, labels = setOf("es"))))
		assertEquals(
			listOf("labels.es" to ErrorCode.FIELD_REQUIRED, "labels.en" to ErrorCode.FIELD_REQUIRED),
			pairs(CatalogRules.activation(active = true, labels = emptySet())),
		)
		// An inactive item can wait for its labels.
		assertEquals(emptyList<Any>(), pairs(CatalogRules.activation(active = false, labels = emptySet())))
	}

	@Test
	fun `the update request has no code, so a code cannot change after creation`() {
		val fields = UpdateItemRequest::class.java.declaredFields.map { it.name }.toSet()
		assertEquals(setOf("labels", "sortOrder", "active", "attributes"), fields)
	}

	@Test
	fun `a code is uppercase letters, digits and underscores`() {
		for (code in listOf("OPEN", "IN_ASSIGNMENT", "A", "KG_2")) assertEquals(true, CatalogRules.CODE.matches(code), code)
		for (code in listOf("open", "IN ASSIGNMENT", "1ST", "", "ÑANDÚ")) assertEquals(false, CatalogRules.CODE.matches(code), code)
	}

	@Test
	fun `a label has a text of 1 to 120 characters, in a supported language`() {
		assertEquals(emptyList<Any>(), pairs(CatalogRules.labels(mapOf("es" to LabelBody("Abierta"), "en" to LabelBody("Open", "Not yet received")))))
		assertEquals(listOf("labels.es.label" to ErrorCode.FIELD_REQUIRED), pairs(CatalogRules.labels(mapOf("es" to LabelBody("  ")))))
		assertEquals(listOf("labels.es.label" to ErrorCode.FIELD_TOO_LONG), pairs(CatalogRules.labels(mapOf("es" to LabelBody("a".repeat(121))))))
		assertEquals(listOf("labels.fr" to ErrorCode.FORMAT_INVALID), pairs(CatalogRules.labels(mapOf("fr" to LabelBody("Ouverte")))))
	}

	@Test
	fun `attributes accept only the keys of the vocabulary, each with its type`() {
		assertEquals(emptyList<Any>(), pairs(CatalogRules.attributes(mapOf("terminal" to true, "decimal_places" to 3))))
		assertEquals(listOf("attributes.color" to ErrorCode.UNKNOWN_FIELD), pairs(CatalogRules.attributes(mapOf("color" to "red"))))
		assertEquals(listOf("attributes.terminal" to ErrorCode.FORMAT_INVALID), pairs(CatalogRules.attributes(mapOf("terminal" to "yes"))))
		assertEquals(emptyList<Any>(), pairs(CatalogRules.attributes(mapOf("country" to "MX"))))
		assertEquals(listOf("attributes.country" to ErrorCode.FORMAT_INVALID), pairs(CatalogRules.attributes(mapOf("country" to "mx"))))
		assertEquals(listOf("attributes.decimal_places" to ErrorCode.FORMAT_INVALID), pairs(CatalogRules.attributes(mapOf("decimal_places" to 9))))
	}
}
