package com.axzydev.stock.feature.stock

import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.stock.ProductTotal
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.stock.StockApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.axzydev.stock.feature.list.RemoteListViewModel

// Each material's stock in each warehouse (docs/modules/stock.md). The app only reads it. A scan narrows the list to the
// material read: its barcode is not in the rows, so the API says which material it is.
class StockViewModel(
	private val api: StockApi,
	private val products: ProductApi,
	private val session: Authorized,
	unreachable: () -> String,
) : RemoteListViewModel<ProductTotal>(session, unreachable) {
	private val mutableQuery = MutableStateFlow("")
	val query: StateFlow<String> = mutableQuery.asStateFlow()

	override suspend fun fetch(token: String): List<ProductTotal> = api.products(token)

	fun edit(value: String) {
		mutableQuery.value = value
	}

	fun scanned(value: String) {
		viewModelScope.launch {
			mutableQuery.value = try {
				session.withToken { token -> products.lookup(token, value.trim()).products.singleOrNull()?.code } ?: value.trim()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				value.trim()
			}
		}
	}
}
