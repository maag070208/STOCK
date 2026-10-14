package com.axzydev.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.axzydev.stock.brand.BrandIntro
import com.axzydev.stock.brand.BrandTheme
import com.axzydev.stock.brand.reducedMotionEnabled
import com.axzydev.stock.data.catalogs.CatalogApi
import com.axzydev.stock.data.customers.CustomersApi
import com.axzydev.stock.data.health.HealthClient
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.receipts.ReceivingApi
import com.axzydev.stock.data.session.AuthApi
import com.axzydev.stock.data.session.secureTokenStorage
import com.axzydev.stock.data.stock.StockApi
import com.axzydev.stock.data.warehouses.WarehouseApi
import com.axzydev.stock.feature.customers.CustomersViewModel
import com.axzydev.stock.feature.lookup.LookupViewModel
import com.axzydev.stock.feature.orders.OrderStatusesViewModel
import com.axzydev.stock.feature.receipts.DiscrepanciesViewModel
import com.axzydev.stock.feature.receipts.ReceiptsViewModel
import com.axzydev.stock.feature.session.SessionState
import com.axzydev.stock.feature.session.SessionViewModel
import com.axzydev.stock.feature.stock.StockViewModel
import com.axzydev.stock.feature.warehouses.WarehousesViewModel
import com.axzydev.stock.i18n.DefaultLanguage
import com.axzydev.stock.i18n.Language
import com.axzydev.stock.i18n.LocalLanguage
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.textFor
import com.axzydev.stock.ui.components.ShellStatusBar
import com.axzydev.stock.ui.customers.CustomersScreen
import com.axzydev.stock.ui.home.HomeScreen
import com.axzydev.stock.ui.lookup.LookupScreen
import com.axzydev.stock.ui.module.ModuleMenuScreen
import com.axzydev.stock.ui.nav.AppBackHandler
import com.axzydev.stock.ui.nav.visibleModulesWithPages
import com.axzydev.stock.ui.orders.OrderStatusesScreen
import com.axzydev.stock.ui.receipts.DiscrepanciesScreen
import com.axzydev.stock.ui.receipts.MyReceiptsScreen
import com.axzydev.stock.ui.signin.ChangePasswordScreen
import com.axzydev.stock.ui.signin.SignInScreen
import com.axzydev.stock.ui.stock.StockScreen
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowTheme
import com.axzydev.stock.ui.warehouses.WarehousesScreen

// The intro plays once per launch of the app.
private object IntroOnce {
	var played = false
}

@Composable
fun App(
	client: HealthClient = remember { HealthClient(platformEngine(), apiBaseUrl) },
	session: SessionViewModel = viewModel {
		SessionViewModel(AuthApi(platformEngine(), apiBaseUrl) { DefaultLanguage }, secureTokenStorage()) {
			textFor(Text.HEALTH_UNREACHABLE, DefaultLanguage)
		}
	},
) {
	// A saved session is looked for while the intro plays.
	LaunchedEffect(session) { session.restore() }
	val state by session.state.collectAsState()
	// Once signed in, the app speaks the person's language. Before that, Spanish.
	val language = (state as? SessionState.SignedIn)?.user?.language?.let { tag -> Language.entries.firstOrNull { it.tag == tag } } ?: DefaultLanguage
	val currentLanguage by rememberUpdatedState(language)
	// One client per part of the API, so the pages of the modules do not each build their own.
	val products = remember { ProductApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val receiving = remember { ReceivingApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val stock = remember { StockApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val warehouses = remember { WarehouseApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val customersApi = remember { CustomersApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val catalogsApi = remember { CatalogApi(platformEngine(), apiBaseUrl) { currentLanguage } }
	val lookup = viewModel {
		LookupViewModel(products, session, { textFor(Text.FIELD_REQUIRED, currentLanguage) }) {
			textFor(Text.HEALTH_UNREACHABLE, currentLanguage)
		}
	}
	// The pages of the modules, each reading its own part of the API. They live with the app, not with a page: their
	// answer is still there when the person comes back to them.
	val noConnection = { textFor(Text.HEALTH_UNREACHABLE, currentLanguage) }
	val receipts = viewModel { ReceiptsViewModel(receiving, session, noConnection) }
	val discrepancies = viewModel { DiscrepanciesViewModel(receiving, session, noConnection) }
	val balances = viewModel { StockViewModel(stock, session, noConnection) }
	val locations = viewModel { WarehousesViewModel(warehouses, session, noConnection) }
	val customers = viewModel { CustomersViewModel(customersApi, session, noConnection) }
	val orderStatuses = viewModel { OrderStatusesViewModel(catalogsApi, session, noConnection) }

	CompositionLocalProvider(LocalLanguage provides language) {
		StockFlowTheme {
			// The workspace canvas, with the shell's strip behind the status bar on every screen.
			Column(Modifier.fillMaxSize().background(StockFlow.colors.background)) {
				ShellStatusBar()
				var introDone by remember { mutableStateOf(IntroOnce.played) }
				if (!introDone) {
					BrandIntro(
						theme = if (isSystemInDarkTheme()) BrandTheme.DARK else BrandTheme.LIGHT,
						reduced = reducedMotionEnabled(),
					) {
						IntroOnce.played = true
						introDone = true
					}
				} else {
					when (val current = state) {
						SessionState.Restoring -> Unit
						is SessionState.SignedOut -> SignInScreen(current, session::signIn)
						is SessionState.PasswordChange -> ChangePasswordScreen(current, session::changePassword)
						is SessionState.SignedIn -> {
							val permissions = current.user.permissions
							val modules = remember(permissions) { visibleModulesWithPages(permissions) }
							// The home is the hub (no module), a module opens its menu (no page), a page is the deepest
							// screen. Both are kept when the phone turns and Android rebuilds the screen.
							var moduleId by rememberSaveable(current.user.id) { mutableStateOf<String?>(null) }
							var pageId by rememberSaveable(current.user.id) { mutableStateOf<String?>(null) }
							val entry = modules.firstOrNull { it.module.id == moduleId }
							val page = entry?.pages?.firstOrNull { it.id == pageId }
							// The system's back closes the deepest thing that is open: the page, then the module. Only
							// at the hub does it leave the app.
							AppBackHandler(enabled = entry != null) {
								if (page != null) pageId = null else moduleId = null
							}
							when {
								entry == null -> HomeScreen(
									client = client,
									user = current.user,
									modules = modules,
									onOpenModule = {
										moduleId = it.id
										pageId = null
									},
									onSignOut = session::signOut,
								)

								page == null -> ModuleMenuScreen(entry, onBack = { moduleId = null }, onOpenPage = { pageId = it.id })

								else -> when (entry.module.id) {
									"receipts" -> if (page.id == DISCREPANCIES) {
										DiscrepanciesScreen(discrepancies) { pageId = null }
									} else {
										MyReceiptsScreen(receipts) { pageId = null }
									}

									"materials" -> LookupScreen(lookup) {
										lookup.clear()
										pageId = null
									}

									"stock" -> StockScreen(balances) { pageId = null }
									"locations" -> WarehousesScreen(locations) { pageId = null }
									"orders" -> when (page.id) {
										CUSTOMERS -> CustomersScreen(customers) { pageId = null }
										ORDER_STATUS -> OrderStatusesScreen(orderStatuses) { pageId = null }
										// The orders page waits for F5, so there is nothing to open yet.
										else -> ModuleMenuScreen(entry, onBack = { moduleId = null }, onOpenPage = { pageId = it.id })
									}

									else -> ModuleMenuScreen(entry, onBack = { moduleId = null }, onOpenPage = { pageId = it.id })
								}
							}
						}
					}
				}
			}
		}
	}
}

private const val DISCREPANCIES = "discrepancies"
private const val CUSTOMERS = "customers"
private const val ORDER_STATUS = "order-status"
