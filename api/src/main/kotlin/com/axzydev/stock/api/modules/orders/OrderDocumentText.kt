package com.axzydev.stock.api.modules.orders

import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.Locale

// The labels the comprobante prints (docs/i18n/documents.md), in es and en. A missing label fails loudly, because the
// document would be wrong for the customer.
data class DocumentLabels(
	val title: String,
	val warehouse: String,
	val number: String,
	val dueDate: String,
	val customer: String,
	val contact: String,
	val lines: String,
	val lineCode: String,
	val lineName: String,
	val lineUnit: String,
	val lineQuantity: String,
	val createdBy: String,
	val operators: String,
	val validatedBy: String,
	val signatureDelivers: String,
	val signatureReceives: String,
	val signatureLine: String,
)

object OrderDocumentText {

	private val source = ReloadableResourceBundleMessageSource().apply {
		setBasename("classpath:messages/documents")
		setDefaultEncoding("UTF-8")
		setFallbackToSystemLocale(false)
	}

	fun labels(language: String): DocumentLabels {
		val locale = Locale.forLanguageTag(language)
		fun get(key: String): String = source.getMessage(key, null, locale)
		return DocumentLabels(
			title = get("document.title"),
			warehouse = get("document.warehouse"),
			number = get("document.number"),
			dueDate = get("document.due_date"),
			customer = get("document.customer"),
			contact = get("document.contact"),
			lines = get("document.lines"),
			lineCode = get("document.line_code"),
			lineName = get("document.line_name"),
			lineUnit = get("document.line_unit"),
			lineQuantity = get("document.line_quantity"),
			createdBy = get("document.created_by"),
			operators = get("document.operators"),
			validatedBy = get("document.validated_by"),
			signatureDelivers = get("document.signature_delivers"),
			signatureReceives = get("document.signature_receives"),
			signatureLine = get("document.signature_line"),
		)
	}
}
