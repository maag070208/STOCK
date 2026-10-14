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
		// Products opens the lookup; the rest of the modules need their own permission.
		assertEquals(
			listOf("materials"),
			visibleModulesWithPages(permissions("products.view" to "ALL")).map { it.module.id },
		)
		assertEquals(
			listOf("materials", "stock"),
			visibleModulesWithPages(permissions("products.view" to "ALL", "stock.view" to "AREA")).map { it.module.id },
		)
	}

	@Test
	fun aScopeOfNoneDoesNotOpenTheModule() {
		assertEquals(
			emptyList(),
			visibleModulesWithPages(permissions("stock.view" to "NONE", "locations.view" to "NONE")).map { it.module.id },
		)
	}

	@Test
	fun aPersonWithoutAnyPermissionSeesNoModule() {
		assertEquals(emptyList(), visibleModulesWithPages(emptyMap()).map { it.module.id })
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
	fun ordersHoldsTheCustomersAndTheStatesTodayAndThePickPageOfAnOperator() {
		val orders = moduleById("orders")!!
		val pages = orders.pages.associateBy { it.id }
		assertFalse(pages.getValue("customers").comingSoon)
		assertEquals(listOf("customers.view"), pages.getValue("customers").anyOf)
		assertFalse(pages.getValue("order-status").comingSoon)
		assertEquals(listOf("catalogs.view"), pages.getValue("order-status").anyOf)
		assertFalse(pages.getValue("my-orders").comingSoon)
		assertEquals(listOf("orders.fulfill"), pages.getValue("my-orders").anyOf)
	}

	@Test
	fun anOperatorWhoFulfillsOpensThePickPage() {
		val entry = visibleModulesWithPages(permissions("orders.fulfill" to "AREA")).single { it.module.id == "orders" }
		assertEquals(listOf("my-orders"), entry.pages.map { it.id })
		assertFalse(entry.comingSoon)
	}

	@Test
	fun ordersIsNotComingSoonOnceOneOfItsPagesOpens() {
		val entry = visibleModulesWithPages(permissions("catalogs.view" to "ALL")).single { it.module.id == "orders" }
		assertEquals(listOf("order-status"), entry.pages.map { it.id })
		assertFalse(entry.comingSoon)
	}
}
