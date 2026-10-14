package com.axzydev.stock.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The home is a hub of the modules the person may use. These cases fix which permissions show which module and which
// page, and that a scope of NONE is the same as not holding the permission at all.
class ModulesTest {

	private fun permissions(vararg pairs: Pair<String, String>) = mapOf(*pairs)

	@Test
	fun theHomeShowsOnlyTheModulesThePermissionsOpen() {
		// Orders is not served by the API yet, so it is a notice and is always listed.
		assertEquals(
			listOf("materials", "orders"),
			visibleModulesWithPages(permissions("products.view" to "ALL")).map { it.id },
		)
	}

	@Test
	fun aScopeOfNoneDoesNotOpenTheModule() {
		assertEquals(
			listOf("orders"),
			visibleModulesWithPages(permissions("stock.view" to "NONE", "locations.view" to "NONE")).map { it.id },
		)
	}

	@Test
	fun withoutTheAppPermissionsThereIsNothingButTheOrdersNotice() {
		assertEquals(listOf("orders"), visibleModulesWithPages(emptyMap()).map { it.id })
	}

	@Test
	fun aModuleListsOnlyThePagesThePersonMayOpen() {
		val receipts = moduleById("receipts")!!
		assertEquals(
			listOf("my-receipts"),
			visiblePages(receipts, permissions("receipts.view" to "OWN")).map { it.id },
		)
		assertEquals(
			listOf("my-receipts", "discrepancies"),
			visiblePages(receipts, permissions("receipts.view" to "OWN", "discrepancies.resolve" to "ALL")).map { it.id },
		)
	}

	@Test
	fun everyModuleAnOperatorReceivesHasAtLeastOnePageToOpen() {
		for (module in MODULES) {
			if (module.comingSoon) continue
			val open = visiblePages(module, permissions(*module.anyOf.map { it to "ALL" }.toTypedArray()))
			assertTrue(open.isNotEmpty(), "${module.id} opens with no page to offer")
		}
	}

	@Test
	fun aModuleTheApiDoesNotServeYetIsListedButItsPageDoesNotOpen() {
		val orders = moduleById("orders")!!
		assertTrue(orders.comingSoon)
		// Its page is still described, so the menu can show what is to come.
		assertFalse(orders.pages.isEmpty())
		assertTrue(orders.anyOf.isEmpty())
	}
}
