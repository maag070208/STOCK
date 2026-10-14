package com.axzydev.stock.api.core.errors

import org.springframework.context.MessageSource
import org.springframework.context.support.ReloadableResourceBundleMessageSource

// Message bundles for error codes, in es (default) and en. Built from docs/i18n/messages.md.
object StockMessages {

	fun source(): MessageSource =
		ReloadableResourceBundleMessageSource().apply {
			setBasename("classpath:messages/errors")
			setDefaultEncoding("UTF-8")
			setFallbackToSystemLocale(false)
			// Every message goes through MessageFormat, with or without values, so an apostrophe reads the same in all of them.
			setAlwaysUseMessageFormat(true)
		}
}
