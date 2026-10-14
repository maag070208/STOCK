package com.axzydev.stock.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.locations.LocationsApi
import com.axzydev.stock.data.orders.Order
import com.axzydev.stock.data.orders.OrderLine
import com.axzydev.stock.data.orders.OrdersApi
import com.axzydev.stock.data.products.ProductApi
import com.axzydev.stock.data.stock.LocationBalance
import com.axzydev.stock.data.stock.StockApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// What the pick page shows: the open orders assigned to the operator, then one of them with the material they scan, the
// locations that hold it, and the quantity they take out of the one they confirm.
data class OrderPickState(
	val loading: Boolean = false,
	val orders: List<Order> = emptyList(),
	val order: Order? = null,
	val scan: String = "",
	val line: OrderLine? = null,
	// The locations of the order's warehouse that hold the scanned material, as the API shows them.
	val balances: List<LocationBalance> = emptyList(),
	val location: LocationBalance? = null,
	val locationScan: String = "",
	val quantity: String = "",
	val fieldError: String? = null,
	val scanError: String? = null,
	val locationError: String? = null,
	val quantityError: String? = null,
	val error: String? = null,
	val saving: Boolean = false,
	val saved: Order? = null,
)

// Picks the material of an order (docs/modules/order-fulfillment.md section 7, barcode-scanning.md section 4). The
// operator opens an order assigned to them, scans the material, sees the balance of each location that holds it, scans
// the code of the one they take it from, and enters the quantity. The API owns the rules and writes the OUT movement;
// the app refuses what it can see is wrong, and keeps the quantity at or below the balance the API showed.
class OrderPickViewModel(
	private val orders: OrdersApi,
	private val products: ProductApi,
	private val stock: StockApi,
	private val locations: LocationsApi,
	private val session: Authorized,
	private val userId: () -> String,
	private val required: () -> String,
	private val quantityInvalid: () -> String,
	private val notInOrder: () -> String,
	private val notInWarehouse: () -> String,
	private val locationEmpty: () -> String,
	private val aboveBalance: () -> String,
	private val aboveLine: () -> String,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(OrderPickState())
	val state: StateFlow<OrderPickState> = mutableState.asStateFlow()

	private var running: Job? = null

	// The camera reads the same label many times a second: the last value it gave is not looked up again.
	private var lastScan: String? = null
	private var lastLocationScan: String? = null

	// The open orders assigned to the signed-in operator.
	fun load() {
		running?.cancel()
		mutableState.value = OrderPickState(loading = true)
		running = viewModelScope.launch {
			mutableState.value = try {
				OrderPickState(orders = session.withToken { token -> orders.open(token, userId()) })
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				OrderPickState(error = e.message)
			} catch (e: Exception) {
				OrderPickState(error = unreachable())
			}
		}
	}

	// The order the operator works on, with an empty form. The list carries the header only, so the order is read again
	// with its lines before anything can be scanned.
	fun open(order: Order) {
		running?.cancel()
		lastScan = null
		lastLocationScan = null
		mutableState.value = OrderPickState(loading = true, order = order)
		running = viewModelScope.launch {
			mutableState.value = try {
				OrderPickState(order = session.withToken { token -> orders.order(token, order.id) })
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				OrderPickState(loading = false, error = e.message)
			} catch (e: Exception) {
				OrderPickState(loading = false, error = unreachable())
			}
		}
	}

	// The hero's back from the form: return to the list and read it fresh.
	fun back() {
		lastScan = null
		lastLocationScan = null
		load()
	}

	// Leaving the page: whatever was open is let go, and the next entry loads it fresh.
	fun clear() {
		running?.cancel()
		lastScan = null
		lastLocationScan = null
		mutableState.value = OrderPickState()
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

	fun editLocationScan(value: String) {
		mutableState.value = mutableState.value.copy(locationScan = value, locationError = null)
	}

	fun scannedLocation(value: String) {
		val clean = value.trim()
		if (clean.isEmpty() || clean == lastLocationScan) return
		lastLocationScan = clean
		confirmLocation(clean)
	}

	fun submitLocation() = confirmLocation(mutableState.value.locationScan.trim())

	// A location from the list the API showed: the operator taps it instead of scanning its code.
	fun choose(balance: LocationBalance) {
		mutableState.value = mutableState.value.copy(location = balance, locationScan = "", locationError = null, quantityError = null)
	}

	fun editQuantity(value: String) {
		mutableState.value = mutableState.value.copy(quantity = value, quantityError = null)
	}

	// The pick, as the operator entered it. A material that is not on the order, a location that does not hold it, or a
	// quantity above the balance the API showed never reaches the API.
	fun save() {
		val current = mutableState.value
		val order = current.order ?: return
		val line = current.line
		val location = current.location
		val quantity = current.quantity.trim()
		when {
			line == null -> {
				mutableState.value = current.copy(fieldError = required())
				return
			}

			location == null -> {
				mutableState.value = current.copy(locationError = required())
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

			// What the location holds, as the API answered it, is the ceiling the app keeps.
			Decimals.above(quantity, location.quantity) -> {
				mutableState.value = current.copy(quantityError = aboveBalance())
				return
			}

			line.remaining?.let { Decimals.above(quantity, it) } == true -> {
				mutableState.value = current.copy(quantityError = aboveLine())
				return
			}
		}
		running?.cancel()
		mutableState.value = current.copy(saving = true, quantityError = null, error = null)
		running = viewModelScope.launch {
			mutableState.value = try {
				val picked = session.withToken { token -> orders.pick(token, order.id, line.id, location.locationId, quantity) }
				OrderPickState(order = picked, saved = picked)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value.copy(saving = false, error = e.message)
			} catch (e: Exception) {
				mutableState.value.copy(saving = false, error = unreachable())
			}
		}
	}

	// Another material of the same order, or the list when nothing is left of it.
	fun another() {
		val picked = mutableState.value.saved ?: return
		lastScan = null
		lastLocationScan = null
		if (picked.open) mutableState.value = OrderPickState(order = picked) else load()
	}

	// The scanned or typed value as a material of the order, then the locations that hold it.
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
					if (line == null) {
						mutableState.value = mutableState.value.copy(scanError = notInOrder(), line = null, balances = emptyList(), location = null, quantity = "")
					} else {
						val balances = stock.balances(token, order.warehouse.id, line.product.id)
						mutableState.value = mutableState.value.copy(scan = "", line = line, balances = balances, location = null, quantity = "")
					}
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

	// A scanned or typed location code as one of the locations the API showed. A code this warehouse does not hold is
	// refused as another warehouse's, and one that holds none of the material as an empty location.
	private fun confirmLocation(value: String) {
		val current = mutableState.value
		val order = current.order ?: return
		if (value.isEmpty()) {
			mutableState.value = current.copy(locationError = required())
			return
		}
		running?.cancel()
		mutableState.value = current.copy(locationScan = value, locationError = null, quantityError = null)
		running = viewModelScope.launch {
			try {
				session.withToken { token ->
					val shown = current.balances.firstOrNull { it.locationCode.equals(value, ignoreCase = true) }
					if (shown != null) {
						mutableState.value = mutableState.value.copy(location = shown, locationScan = "")
					} else {
						val exists = locations.inWarehouse(token, order.warehouse.id, value).any { it.code.equals(value, ignoreCase = true) }
						mutableState.value = mutableState.value.copy(location = null, locationError = if (exists) locationEmpty() else notInWarehouse())
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value = mutableState.value.copy(locationError = e.message)
			} catch (e: Exception) {
				mutableState.value = mutableState.value.copy(locationError = unreachable())
			}
		}
	}
}
