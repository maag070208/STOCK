package com.axzydev.stock.ui.nav

import com.axzydev.stock.i18n.Text
import com.axzydev.stock.ui.components.AppIcon

// The home is a hub: it shows one button per module the person may use, and each module opens its own menu of pages.
// A page that the API does not have yet is still listed, marked `comingSoon`, so the shape of the app is visible.
// This file is the single place where the modules and their pages are listed; adding one is adding a line here and a
// screen in the navigation of App.kt.

// A page of a module: a row in the module's menu, and a screen.
data class ModulePage(
	val id: String,
	val title: Text,
	val hint: Text,
	val icon: AppIcon,
	// The page shows when the person holds any of these permissions. Empty: any signed-in person.
	val anyOf: List<String> = emptyList(),
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
	// The module is listed, but its pages are not built yet.
	val comingSoon: Boolean = false,
)

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
	// F5 has not been built: orders live in the API's roadmap, not in it yet (docs/phases/F5-orders.md).
	Module(
		id = "orders",
		title = Text.MODULE_ORDERS,
		hint = Text.MODULE_ORDERS_HINT,
		icon = AppIcon.CLIPBOARD,
		comingSoon = true,
		pages = listOf(
			ModulePage("my-orders", Text.PAGE_ORDERS, Text.PAGE_ORDERS_HINT, AppIcon.CLIPBOARD),
		),
	),
)

// A permission is held when its scope is above NONE.
private fun Map<String, String>.holds(key: String): Boolean = (this[key] ?: "NONE") != "NONE"

fun visibleModules(permissions: Map<String, String>): List<Module> =
	MODULES.filter { it.anyOf.isEmpty() || it.anyOf.any(permissions::holds) }

// The pages of a module the person may open. A module with no visible page is not shown on the home.
fun visiblePages(module: Module, permissions: Map<String, String>): List<ModulePage> =
	module.pages.filter { it.anyOf.isEmpty() || it.anyOf.any(permissions::holds) }

fun visibleModulesWithPages(permissions: Map<String, String>): List<Module> =
	visibleModules(permissions).filter { it.comingSoon || visiblePages(it, permissions).isNotEmpty() }

fun moduleById(id: String): Module? = MODULES.firstOrNull { it.id == id }
