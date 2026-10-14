package com.axzydev.stock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// F0-12 · unit: the app's name and version are shared by every platform.
class AppInfoTest {

	@Test
	fun `the app is named StockFlow and has a version`() {
		assertEquals("StockFlow", AppInfo.NAME)
		assertTrue(AppInfo.VERSION.isNotBlank())
	}
}
