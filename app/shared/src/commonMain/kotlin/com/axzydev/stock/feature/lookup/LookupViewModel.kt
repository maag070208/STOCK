package com.axzydev.stock.feature.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.products.LookupProduct
import com.axzydev.stock.data.products.ProductApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// What the lookup screen shows. `candidates` are several products found by name, from which the person picks;
// `product` is the one found or picked. Nothing is chosen for the person when there are several.
data class LookupState(
	val query: String = "",
	val searching: Boolean = false,
	val product: LookupProduct? = null,
	val candidates: List<LookupProduct> = emptyList(),
	val error: String? = null,
	val fieldError: String? = null,
)

// Turns a scanned or typed value into a lookup (docs/modules/barcode-scanning.md section 3). A scan never creates
// anything: it only finds an existing product. Messages come from the API in the person's language; `required` and
// `unreachable` are the app's own.
class LookupViewModel(
	private val api: ProductApi,
	private val session: Authorized,
	private val required: () -> String,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(LookupState())
	val state: StateFlow<LookupState> = mutableState.asStateFlow()

	private var running: Job? = null

	// The camera reads the same label many times a second: the last value it gave is not looked up again.
	private var lastScan: String? = null

	fun edit(query: String) {
		mutableState.value = mutableState.value.copy(query = query, fieldError = null)
	}

	// The camera read a value: it is looked up at once, as if typed.
	fun scanned(value: String) {
		val clean = value.trim()
		if (clean.isEmpty() || clean == lastScan) return
		lastScan = clean
		search(clean)
	}

	fun submit() = search(mutableState.value.query.trim())

	// A product from the list of several.
	fun choose(product: LookupProduct) {
		mutableState.value = mutableState.value.copy(product = product, candidates = emptyList())
	}

	fun clear() {
		running?.cancel()
		lastScan = null
		mutableState.value = LookupState()
	}

	private fun search(value: String) {
		if (value.isEmpty()) {
			mutableState.value = mutableState.value.copy(fieldError = required())
			return
		}
		running?.cancel()
		mutableState.value = LookupState(query = value, searching = true)
		running = viewModelScope.launch {
			mutableState.value = try {
				val result = session.withToken { token -> api.lookup(token, value) }
				// One product is shown; several, found by name, are a list to pick from.
				val single = result.products.singleOrNull()
				if (single != null) LookupState(query = value, product = single) else LookupState(query = value, candidates = result.products)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				LookupState(query = value, error = e.message)
			} catch (e: Exception) {
				LookupState(query = value, error = unreachable())
			}
		}
	}
}
