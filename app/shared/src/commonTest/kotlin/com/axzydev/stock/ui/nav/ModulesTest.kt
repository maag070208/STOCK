package com.axzydev.stock.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The home is a hub of the modules the person may use. These cases fix which permissions show which module and which
// page, that a scope of NONE is the same as not holding the permission at all, and that a module with real and planned
// pages is only "coming soon" for someone who cannot open the real ones.
class ModulesTest {

	private fun permissions(vararg pairs: Pair<String, String>) = mapOf(*pairs)

	@Test
	fun theHomeShowsOnlyTheModulesThePermissionsOpen() {
		// Customers is open to whoever may read customers; the orders notice needs no permission, so it is always listed.
		assertEquals(
			listOf("materials", "orders"),
			visibleModulesWithPages(permissions("products.view" to "ALL")).map { it.module.id },
		)
	}

	@Test
	fun aScopeOfNoneDoesNotOpenTheModule() {
		assertEquals(
			listOf("orders"),
			visibleModulesWithPages(permissions("stock.view" to "NONE", "locations.view" to "NONE")).map { it.module.id },
		)
	}

	@Test
	fun theOrdersNoticeAndNothingElseShowWithoutAnyPermission() {
		assertEquals(listOf("orders"), visibleModulesWithPages(emptyMap()).map { it.module.id })
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
	fun anOperatorWhoReceivesOpensCountAndPlacement() {
		val receipts = moduleById("receipts")!!
		val pages = visiblePages(
			receipts,
			permissions("receipts.view" to "OWN", "receipts.receive" to "OWN", "receipts.place" to "OWN"),
		).map { it.id }
		assertEquals(listOf("my-receipts", "count", "placement"), pages)
	}

	@Test
	fun everyModuleAnOperatorReceivesHasAtLeastOnePageToOpen() {
		for (module in MODULES) {
			if (module.anyOf.isEmpty()) continue
			val open = visiblePages(module, permissions(*module.anyOf.map { it to "ALL" }.toTypedArray()))
			assertTrue(open.isNotEmpty(), "${module.id} opens with no page to offer")
		}
	}

	@Test
	fun ordersHoldsTheCustomersAndTheStatesTodayAndTheOrdersPageWaitsForF5() {
		val orders = moduleById("orders")!!
		val pages = orders.pages.associateBy { it.id }
		assertFalse(pages.getValue("customers").comingSoon)
		assertEquals(listOf("customers.view"), pages.getValue("customers").anyOf)
		assertFalse(pages.getValue("order-status").comingSoon)
		assertEquals(listOf("catalogs.view"), pages.getValue("order-status").anyOf)
		assertTrue(pages.getValue("my-orders").comingSoon)
	}

	@Test
	fun ordersIsStillComingSoonForSomeoneWhoCannotReadItsRealPages() {
		// An operator holding neither customers.view nor catalogs.view only sees the planned page, so the module is
		// still a notice to them.
		val entry = visibleModulesWithPages(permissions("stock.view" to "ALL")).single { it.module.id == "orders" }
		assertEquals(listOf("my-orders"), entry.pages.map { it.id })
		assertTrue(entry.comingSoon)
	}

	@Test
	fun ordersIsNotComingSoonOnceOneOfItsPagesOpens() {
		val entry = visibleModulesWithPages(permissions("catalogs.view" to "ALL")).single { it.module.id == "orders" }
		assertEquals(listOf("order-status", "my-orders"), entry.pages.map { it.id })
		assertFalse(entry.comingSoon)
	}
}
