package com.axzydev.stock.api.core.errors

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

@Configuration
class LocaleConfig {

	// Spanish is the default. English is used only when the request asks for it.
	@Bean
	fun localeResolver(): LocaleResolver =
		AcceptHeaderLocaleResolver().apply {
			setDefaultLocale(Locale.forLanguageTag("es"))
			setSupportedLocales(listOf(Locale.forLanguageTag("es"), Locale.forLanguageTag("en")))
		}

	@Bean
	fun messageSource(): MessageSource = StockMessages.source()
}
