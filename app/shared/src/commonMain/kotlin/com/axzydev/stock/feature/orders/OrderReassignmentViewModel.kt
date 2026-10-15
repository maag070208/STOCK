package com.axzydev.stock.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.orders.Order
import com.axzydev.stock.data.orders.OrderAlert
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.data.orders.OrdersApi
import com.axzydev.stock.data.products.ProductApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// An open order that still needs the scanned material, with the line that needs it: the target of a move.
data class ReassignmentTarget(val order: Order, val line: OrderLine)

// What the reassignment page shows: the alerts of the signed-in person, then the order of one of them with the material
// they scan, the orders that need it, and the quantity and note of the move.
data class OrderReassignmentState(
	val loading: Boolean = false,
	val alerts: List<OrderAlert> = emptyList(),
	val error: String? = null,
	val order: Order? = null,
	val scan: String = "",
	val line: OrderLine? = null,
	val targets: List<ReassignmentTarget> = emptyList(),
	val target: ReassignmentTarget? = null,
	val quantity: String = "",
	val note: String = "",
	val fieldError: String? = null,
	val scanError: String? = null,
	val targetError: String? = null,
	val quantityError: String? = null,
	val noteError: String? = null,
	val saving: Boolean = false,
	val saved: Order? = null,
)

// Reassigns picked material (docs/modules/order-fulfillment.md section 8, barcode-scanning.md section 4). The person
// opens the alert of an order, scans the material that was already picked, and moves a quantity of it to another open
// order of the same warehouse that needs it. No stock changes: the material is already out of the shelves.
class OrderReassignmentViewModel(
	private val orders: OrdersApi,
	private val products: ProductApi,
	private val session: Authorized,
	private val required: () -> String,
	private val quantityInvalid: () -> String,
	private val nothingPicked: () -> String,
	private val abovePicked: () -> String,
	private val aboveTarget: () -> String,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(OrderReassignmentState())
	val state: StateFlow<OrderReassignmentState> = mutableState.asStateFlow()

	private var running: Job? = null

	// The camera reads the same label many times a second: the last value it gave is not looked up again.
	private var lastScan: String? = null

	// The alerts of the signed-in person: the orders that are due, or about to be (section 7a).
	fun load() {
		running?.cancel()
		mutableState.value = OrderReassignmentState(loading = true)
		running = viewModelScope.launch {
			mutableState.value = try {
				OrderReassignmentState(alerts = session.withToken { token -> orders.alerts(token) })
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				OrderReassignmentState(error = e.message)
			} catch (e: Exception) {
				OrderReassignmentState(error = unreachable())
			}
		}
	}

	// The order of one alert, read with its lines before anything is scanned.
	fun open(alert: OrderAlert) {
		running?.cancel()
		lastScan = null
		mutableState.value = OrderReassignmentState(loading = true, order = alert.order)
		running = viewModelScope.launch {
			mutableState.value = try {
				OrderReassignmentState(order = session.withToken { token -> orders.order(token, alert.order.id) })
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				OrderReassignmentState(loading = false, error = e.message)
			} catch (e: Exception) {
				OrderReassignmentState(loading = false, error = unreachable())
			}
		}
	}

	// The hero's back from the form: the alerts are read again.
	fun back() {
		lastScan = null
		load()
	}

	// Leaving the page: whatever was open is let go, and the next entry loads it fresh.
	fun clear() {
		running?.cancel()
		lastScan = null
		mutableState.value = OrderReassignmentState()
	}

	fun editScan(value: String) {
		mutableState.value = mutableState.value.copy(scan = value, fieldError = null)
	}

	fun scanned(value: String) {
		val clean = value.trim()
		if (clean.isEmpty() || clean == lastScan) return
		lastScan = clean
		find(clean)
	}

	fun submitScan() = find(mutableState.value.scan.trim())

	// The target order, from the list the app showed.
	fun choose(target: ReassignmentTarget) {
		mutableState.value = mutableState.value.copy(target = target, targetError = null, quantityError = null)
	}

	fun editQuantity(value: String) {
		mutableState.value = mutableState.value.copy(quantity = value, quantityError = null)
	}

	fun editNote(value: String) {
		mutableState.value = mutableState.value.copy(note = value, noteError = null)
	}

	// The move, as the person entered it. More than what is picked, or more than the target needs, never reaches the API.
	fun save() {
		val current = mutableState.value
		val order = current.order ?: return
		val line = current.line
		val target = current.target
		val quantity = current.quantity.trim()
		val note = current.note.trim()
		when {
			line == null -> {
				mutableState.value = current.copy(fieldError = required())
				return
			}

			target == null -> {
				mutableState.value = current.copy(targetError = required())
				return
			}

			quantity.isEmpty() -> {
				mutableState.value = current.copy(quantityError = required())
				return
			}

			!Decimals.isValid(quantity) || Decimals.same(quantity, "0") -> {
				mutableState.value = current.copy(quantityError = quantityInvalid())
				return
			}

			// What the line has already picked is the ceiling the app keeps.
			Decimals.above(quantity, line.pickedQuantity) -> {
				mutableState.value = current.copy(quantityError = abovePicked())
				return
			}

			target.line.remaining?.let { Decimals.above(quantity, it) } == true -> {
				mutableState.value = current.copy(quantityError = aboveTarget())
				return
			}

			note.isEmpty() -> {
				mutableState.value = current.copy(noteError = required())
				return
			}
		}
		running?.cancel()
		mutableState.value = current.copy(saving = true, quantityError = null, noteError = null, error = null)
		running = viewModelScope.launch {
			mutableState.value = try {
				val moved = session.withToken { token -> orders.transfer(token, order.id, line.id, target.order.id, quantity, note) }
				OrderReassignmentState(order = moved, saved = moved)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value.copy(saving = false, error = e.message)
			} catch (e: Exception) {
				mutableState.value.copy(saving = false, error = unreachable())
			}
		}
	}

	// Another material of the same order: the alerts are read again, because the move may have left the order without
	// anything to reassign.
	fun another() = load()

	// The scanned or typed value as a picked line of the order, then the orders that still need that material.
	private fun find(value: String) {
		val current = mutableState.value
		val order = current.order ?: return
		if (value.isEmpty()) {
			mutableState.value = current.copy(fieldError = required())
			return
		}
		running?.cancel()
		mutableState.value = current.copy(scan = value, fieldError = null, scanError = null)
		running = viewModelScope.launch {
			try {
				session.withToken { token ->
					val found = products.lookup(token, value).products
					val lines = order.lines.orEmpty()
					// A barcode or a code gives one material; a name gives several, and only the ones on the order count.
					val onOrder = found.filter { product -> lines.any { it.product.id == product.id } }
					val line = when {
						found.size == 1 -> lines.firstOrNull { it.product.id == found.single().id }
						onOrder.size == 1 -> lines.firstOrNull { it.product.id == onOrder.single().id }
						else -> null
					}
					// Only material that was already picked can be moved (section 8).
					val picked = line?.takeIf { Decimals.above(it.pickedQuantity, "0") }
					if (picked == null) {
						mutableState.value = mutableState.value.copy(scanError = nothingPicked(), line = null, targets = emptyList(), target = null, quantity = "")
						return@withToken
					}
					val targets = targetsFor(token, order, picked)
					mutableState.value = mutableState.value.copy(scan = "", line = picked, targets = targets, target = null, quantity = "")
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value = mutableState.value.copy(scanError = e.message)
			} catch (e: Exception) {
				mutableState.value = mutableState.value.copy(scanError = unreachable())
			}
		}
	}

	// The open orders of the same warehouse whose line for this material still needs some of it. Each candidate is read
	// with its lines: the list carries the header only.
	private suspend fun targetsFor(token: String, source: Order, line: OrderLine): List<ReassignmentTarget> =
		orders.openInWarehouse(token, source.warehouse.id)
			.filter { it.id != source.id && it.open && it.warehouse.id == source.warehouse.id }
			.mapNotNull { candidate ->
				val full = orders.order(token, candidate.id)
				full.lines.orEmpty()
					.firstOrNull { it.product.id == line.product.id && Decimals.above(it.remaining ?: "0", "0") }
					?.let { ReassignmentTarget(full, it) }
			}
}
