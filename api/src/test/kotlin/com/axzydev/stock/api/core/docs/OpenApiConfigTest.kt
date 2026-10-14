package com.axzydev.stock.api.core.docs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// F0-06 · unit: the OpenAPI document covers the /api/v1 prefix and names the API.
class OpenApiConfigTest {

	@Test
	fun `the document covers the versioned API and is titled StockFlow`() {
		assertEquals("/api/v1/**", OpenApiConfig.PATHS_TO_MATCH)
		assertEquals("StockFlow API", OpenApiConfig.openApi().info.title)
	}
}
