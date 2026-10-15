package com.axzydev.stock.api.core.i18n

import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.Locale

// The labels the API prints on its documents — the comprobante of an order and the reports it exports — in es and en
// (docs/i18n/documents.md). A missing label fails loudly: a document with a hole in it is worse than no document.
object DocumentLabels {

	private val source = ReloadableResourceBundleMessageSource().apply {
		setBasename("classpath:messages/documents")
		setDefaultEncoding("UTF-8")
		setFallbackToSystemLocale(false)
	}

	fun text(key: String, language: String): String = source.getMessage(key, null, Locale.forLanguageTag(language))
}
