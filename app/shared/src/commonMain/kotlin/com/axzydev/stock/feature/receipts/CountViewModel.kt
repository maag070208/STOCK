package com.axzydev.stock.feature.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.products.LookupProduct
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.receipts.AssignmentView
import com.axzydev.stock.data.receipts.ReceivingApi
import com.axzydev.stock.data.receipts.Ref
import com.axzydev.stock.data.receipts.UnitRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One assignment still to count: the invoice it belongs to, the line's material and unit, and the operator's own share.
data class CountTarget(
	val assignment: AssignmentView,
	val invoice: String,
	val product: Ref,
	val unit: UnitRef?,
)

// What the count page shows. The operator scans a material; the app finds the open assignments of that material, opens
// the one when it is alone, and lists them when there are several. The count form then takes the counted quantity.
data class CountState(
	val query: String = "",
	val looking: Boolean = false,
	// Several products matched the value by name: the operator picks one before the assignments are looked for.
	val products: List<LookupProduct> = emptyList(),
	// The open assignments of the material found: one is opened, several are a list to pick from.
	val targets: List<CountTarget> = emptyList(),
	val target: CountTarget? = null,
	val quantity: String = "",
	val notes: String = "",
	val fieldError: String? = null,
	val quantityError: String? = null,
	val error: String? = null,
	val saving: Boolean = false,
	val saved: AssignmentView? = null,
)

// Counts what arrived (docs/modules/material-receiving.md section 4, barcode-scanning.md section 3). A scan finds the
// operator's open assignment of that material; they never pick a quantity that is not there. The count itself is
// written by the API, which owns the rules; the app only refuses what it can see is not a quantity at all.
class CountViewModel(
	private val products: ProductApi,
	private val receiving: ReceivingApi,
	private val session: Authorized,
	private val required: () -> String,
	private val quantityInvalid: () -> String,
	private val notInAssignment: () -> String,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(CountState())
	val state: StateFlow<CountState> = mutableState.asStateFlow()

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
		find(clean)
	}

	fun submit() = find(mutableState.value.query.trim())

	// A material from the list of several the name matched.
	fun choose(product: LookupProduct) {
		running?.cancel()
		val base = mutableState.value.copy(products = emptyList(), looking = true)
		mutableState.value = base
		running = viewModelScope.launch {
			mutableState.value = guarded(base) { token -> targetsFor(token, base.query, product.id) }
		}
	}

	// The assignment to count, from the list of several.
	fun choose(target: CountTarget) {
		mutableState.value = mutableState.value.copy(targets = emptyList(), target = target)
	}

	fun editQuantity(value: String) {
		mutableState.value = mutableState.value.copy(quantity = value, quantityError = null)
	}

	fun editNotes(value: String) {
		mutableState.value = mutableState.value.copy(notes = value)
	}

	// The count, as the operator sees it. A blank or a negative quantity never reaches the API.
	fun save() {
		val current = mutableState.value
		val target = current.target ?: return
		val quantity = current.quantity.trim()
		when {
			quantity.isEmpty() -> mutableState.value = current.copy(quantityError = required())
			!QUANTITY.matches(quantity) -> mutableState.value = current.copy(quantityError = quantityInvalid())
			else -> {
				running?.cancel()
				mutableState.value = current.copy(saving = true, quantityError = null, error = null)
				running = viewModelScope.launch {
					mutableState.value = guard {
						val saved = session.withToken { token -> receiving.count(token, target.assignment.id, quantity, current.notes) }
						mutableState.value.copy(saving = false, saved = saved)
					}
				}
			}
		}
	}

	fun clear() {
		running?.cancel()
		lastScan = null
		mutableState.value = CountState()
	}

	// The scanned or typed value as a material, then the operator's open assignments of it.
	private fun find(value: String) {
		if (value.isEmpty()) {
			mutableState.value = mutableState.value.copy(fieldError = required())
			return
		}
		running?.cancel()
		mutableState.value = CountState(query = value, looking = true)
		running = viewModelScope.launch {
			val base = mutableState.value
			mutableState.value = guarded(base) { token ->
				val result = products.lookup(token, value)
				val single = result.products.singleOrNull()
				// One material is looked through; several, found by name, are a list to pick from first.
				if (single == null) base.copy(looking = false, products = result.products)
				else targetsFor(token, value, single.id)
			}
		}
	}

	// The open assignments of one material: the invoices still PENDING and this operator's own lines.
	private suspend fun targetsFor(token: String, query: String, productId: String): CountState {
		val targets = receiving.mine(token)
			.filter { it.invoice.status.code == OPEN }
			.flatMap { receipt ->
				receipt.lines
					.filter { it.product.id == productId }
					.flatMap { line -> line.assignments.map { CountTarget(it, receipt.invoice.number, line.product, line.unit) } }
			}
		return when {
			targets.isEmpty() -> CountState(query = query, error = notInAssignment())
			targets.size == 1 -> CountState(query = query, target = targets.single())
			else -> CountState(query = query, targets = targets)
		}
	}

	// A lookup or a read that failed leaves the query as it was and shows the API's own message, or the app's when
	// there is no connection.
	private suspend fun guarded(base: CountState, block: suspend (token: String) -> CountState): CountState =
		try {
			session.withToken(block)
		} catch (e: CancellationException) {
			throw e
		} catch (e: ApiError) {
			base.copy(looking = false, error = e.message)
		} catch (e: Exception) {
			base.copy(looking = false, error = unreachable())
		}

	private suspend fun guard(block: suspend () -> CountState): CountState =
		try {
			block()
		} catch (e: CancellationException) {
			throw e
		} catch (e: ApiError) {
			mutableState.value.copy(saving = false, error = e.message)
		} catch (e: Exception) {
			mutableState.value.copy(saving = false, error = unreachable())
		}

	private companion object {
		// A counted quantity is a non-negative decimal, as the API's own rule reads it (InvoiceRules.counted). A
		// negative or a letter never leaves the app.
		val QUANTITY = Regex("^[0-9]+(\\.[0-9]+)?$")

		// The invoice statuses whose assignments are still to count (docs/modules/material-receiving.md section 4).
		const val OPEN = "PENDING"
	}
}
