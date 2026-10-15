package com.axzydev.stock.ui.nav

import com.axzydev.stock.i18n.Text
import com.axzydev.stock.ui.components.AppIcon

// The home is a hub: it shows one button per module the person may use, and each module opens its own menu of pages.
// A page that the API does not have yet is still listed, marked `comingSoon`, so the shape of the app is visible.
// This file is the single place where the modules and their pages are listed; adding one is adding a line here and a
// screen in the navigation of App.kt.

// A page of a module: a row in the module's menu, and a screen. A page the API does not serve yet is marked
// `comingSoon`: it keeps its place, so the shape of the app is visible before the phase that fills it, but it says so
// and does not open.
data class ModulePage(
	val id: String,
	val title: Text,
	val hint: Text,
	val icon: AppIcon,
	// The page shows when the person holds any of these permissions. Empty: any signed-in person.
	val anyOf: List<String> = emptyList(),
	val comingSoon: Boolean = false,
)

// A module: a button on the home, with its own menu of pages.
data class Module(
	val id: String,
	val title: Text,
	val hint: Text,
	val icon: AppIcon,
	val pages: List<ModulePage>,
	// The module shows when the person holds any of these permissions. Empty: any signed-in person.
	val anyOf: List<String> = emptyList(),
)

// A module as the home shows it: the module and the pages this person may open. Everything it offers is still to come
// when it holds nothing else, and then the home and its menu say so. It is worked out, not a flag, so a module that
// only opens real pages for an administrator still says "coming soon" to someone who can only see the planned one.
data class ModuleEntry(val module: Module, val pages: List<ModulePage>) {
	val comingSoon: Boolean get() = pages.isNotEmpty() && pages.all { it.comingSoon }
}

// The modules of the app, in the order of the home. Their permissions are the API's (PermissionCatalog).
val MODULES: List<Module> = listOf(
	Module(
		id = "receipts",
		title = Text.MODULE_RECEIPTS,
		hint = Text.MODULE_RECEIPTS_HINT,
		icon = AppIcon.INBOX,
		anyOf = listOf("receipts.view"),
		pages = listOf(
			ModulePage("my-receipts", Text.PAGE_MY_RECEIPTS, Text.PAGE_MY_RECEIPTS_HINT, AppIcon.INBOX),
			ModulePage("count", Text.PAGE_COUNT, Text.PAGE_COUNT_HINT, AppIcon.SCAN, anyOf = listOf("receipts.receive")),
			ModulePage("placement", Text.PAGE_PLACEMENT, Text.PAGE_PLACEMENT_HINT, AppIcon.WAREHOUSE, anyOf = listOf("receipts.place")),
			ModulePage("discrepancies", Text.PAGE_DISCREPANCIES, Text.PAGE_DISCREPANCIES_HINT, AppIcon.ALERT, anyOf = listOf("discrepancies.resolve")),
		),
	),
	Module(
		id = "materials",
		title = Text.MODULE_MATERIALS,
		hint = Text.MODULE_MATERIALS_HINT,
		icon = AppIcon.SEARCH,
		anyOf = listOf("products.view"),
		pages = listOf(
			ModulePage("lookup", Text.PAGE_LOOKUP, Text.PAGE_LOOKUP_HINT, AppIcon.SEARCH),
		),
	),
	Module(
		id = "stock",
		title = Text.MODULE_STOCK,
		hint = Text.MODULE_STOCK_HINT,
		icon = AppIcon.BOXES,
		anyOf = listOf("stock.view"),
		pages = listOf(
			ModulePage("balances", Text.PAGE_STOCK, Text.PAGE_STOCK_HINT, AppIcon.BOXES),
		),
	),
	Module(
		id = "locations",
		title = Text.MODULE_LOCATIONS,
		hint = Text.MODULE_LOCATIONS_HINT,
		icon = AppIcon.WAREHOUSE,
		anyOf = listOf("locations.view"),
		pages = listOf(
			ModulePage("warehouses", Text.PAGE_WAREHOUSES, Text.PAGE_WAREHOUSES_HINT, AppIcon.WAREHOUSE),
		),
	),
	// F5: orders. The operator fulfills them from the app (F5-18); the customers an order is for and the states it goes
	// through are the pages that read the API today.
	Module(
		id = "orders",
		title = Text.MODULE_ORDERS,
		hint = Text.MODULE_ORDERS_HINT,
		icon = AppIcon.CLIPBOARD,
		pages = listOf(
			ModulePage("customers", Text.PAGE_CUSTOMERS, Text.PAGE_CUSTOMERS_HINT, AppIcon.USER, anyOf = listOf("customers.view")),
			ModulePage("order-status", Text.PAGE_ORDER_STATUS, Text.PAGE_ORDER_STATUS_HINT, AppIcon.CLIPBOARD, anyOf = listOf("catalogs.view")),
			ModulePage("my-orders", Text.PAGE_ORDERS, Text.PAGE_ORDERS_HINT, AppIcon.CLIPBOARD, anyOf = listOf("orders.fulfill")),
			// F5-19: the alerts of the day, and moving picked material to another order.
			ModulePage("reassignment", Text.PAGE_REASSIGNMENT, Text.PAGE_REASSIGNMENT_HINT, AppIcon.ALERT, anyOf = listOf("orders.reassign")),
		),
	),
)

// A permission is held when its scope is above NONE.
private fun Map<String, String>.holds(key: String): Boolean = (this[key] ?: "NONE") != "NONE"

fun visibleModules(permissions: Map<String, String>): List<Module> =
	MODULES.filter { it.anyOf.isEmpty() || it.anyOf.any(permissions::holds) }

// The pages of a module the person may open.
fun visiblePages(module: Module, permissions: Map<String, String>): List<ModulePage> =
	module.pages.filter { it.anyOf.isEmpty() || it.anyOf.any(permissions::holds) }

// The modules of the home, each with the pages this person may open. A module with no page to open is not shown.
fun visibleModulesWithPages(permissions: Map<String, String>): List<ModuleEntry> =
	visibleModules(permissions).mapNotNull { module ->
		visiblePages(module, permissions).takeIf { it.isNotEmpty() }?.let { ModuleEntry(module, it) }
	}

fun moduleById(id: String): Module? = MODULES.firstOrNull { it.id == id }
