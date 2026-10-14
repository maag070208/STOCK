package com.axzydev.stock.api.core.docs

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// The document covers the versioned API only. The UI is served at /docs, the JSON at /docs/json.
@Configuration
class OpenApiConfig {

	companion object {
		const val PATHS_TO_MATCH = "/api/v1/**"

		fun openApi(): OpenAPI = OpenAPI().info(Info().title("StockFlow API").version("v1"))
	}

	@Bean
	fun stockFlowOpenApi(): OpenAPI = openApi()
}
