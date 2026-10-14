package com.axzydev.stock.feature.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.locations.LocationsApi
import com.axzydev.stock.data.receipts.AssignmentView
import com.axzydev.stock.data.receipts.PlacementBody
import com.axzydev.stock.data.receipts.ReceivingApi
import com.axzydev.stock.data.receipts.Ref
import com.axzydev.stock.data.receipts.UnitRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One location the operator is putting counted goods into, with the quantity they entered.
data class PlacementLine(val locationId: String, val locationCode: String, val quantity: String)

// One counted assignment still to place: the invoice and its warehouse, the material, and the operator's own share.
data class PlacementTarget(
	val assignment: AssignmentView,
	val invoice: String,
	val warehouseId: String,
	val product: Ref,
	val unit: UnitRef?,
)

// What the placement page shows. The operator picks what they counted, scans the code of each location they put it in,
// and enters how much goes there. The app keeps the sum equal to what is left to place, so nothing is lost or doubled.
data class PlacementState(
	val loading: Boolean = false,
	val targets: List<PlacementTarget> = emptyList(),
	val target: PlacementTarget? = null,
	// What is still to place for the target: counted minus what is already placed.
	val remaining: String = "",
	val scan: String = "",
	val lines: List<PlacementLine> = emptyList(),
	val error: String? = null,
	val scanError: String? = null,
	val lineError: String? = null,
	val saving: Boolean = false,
	val saved: AssignmentView? = null,
) {
	// What the operator has entered so far, or null when a row is not a quantity.
	val placedTotal: String? get() = Decimals.sum(lines.map { it.quantity })

	// What no row has claimed yet: the remaining quantity minus what the rows add up to.
	val unassigned: String? get() = placedTotal?.let { Decimals.subtract(remaining, it) }
}

// Puts counted goods into locations (docs/modules/material-receiving.md section 7, barcode-scanning.md section 4). The
// location is found by its code, in the warehouse of the invoice, so another warehouse's code is refused here. The sum
// of the rows is kept equal to what was counted; the API writes the movements that the stock is derived from.
class PlacementViewModel(
	private val receiving: ReceivingApi,
	private val locations: LocationsApi,
	private val session: Authorized,
	private val required: () -> String,
	private val quantityInvalid: () -> String,
	private val notInWarehouse: () -> String,
	private val totalMismatch: () -> String,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(PlacementState())
	val state: StateFlow<PlacementState> = mutableState.asStateFlow()

	private var running: Job? = null
	private var lastScan: String? = null

	// What the operator still has to place: their counted assignments of the invoices in assignment.
	fun load() {
		running?.cancel()
		mutableState.value = PlacementState(loading = true)
		running = viewModelScope.launch {
			mutableState.value = try {
				val targets = session.withToken { token -> targetsOf(token) }
				PlacementState(targets = targets)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				PlacementState(error = e.message)
			} catch (e: Exception) {
				PlacementState(error = unreachable())
			}
		}
	}

	fun open(target: PlacementTarget) {
		running?.cancel()
		lastScan = null
		val remaining = Decimals.subtract(target.assignment.countedQuantity.orEmpty(), target.assignment.placedQuantity) ?: ""
		mutableState.value = PlacementState(targets = emptyList(), target = target, remaining = remaining)
	}

	// The hero's back from the form: return to the list and read it fresh.
	fun back() {
		lastScan = null
		load()
	}

	// Leaving the page: whatever was open is let go, and the next entry loads it fresh.
	fun clear() {
		running?.cancel()
		lastScan = null
		mutableState.value = PlacementState()
	}

	fun editScan(value: String) {
		mutableState.value = mutableState.value.copy(scan = value, scanError = null)
	}

	// The camera read a location code: it is put in the list at once.
	fun scanned(value: String) {
		val clean = value.trim()
		if (clean.isEmpty() || clean == lastScan) return
		lastScan = clean
		addLocation(clean)
	}

	fun addLocation() = addLocation(mutableState.value.scan.trim())

	fun editQuantity(index: Int, value: String) {
		val lines = mutableState.value.lines.mapIndexed { i, line -> if (i == index) line.copy(quantity = value) else line }
		mutableState.value = mutableState.value.copy(lines = lines, lineError = null)
	}

	fun remove(index: Int) {
		mutableState.value = mutableState.value.copy(lines = mutableState.value.lines.filterIndexed { i, _ -> i != index }, lineError = null)
	}

	// The placements as the operator entered them. A row that is not a quantity, or a sum that is not what was
	// counted, never reaches the API.
	fun save() {
		val current = mutableState.value
		val target = current.target ?: return
		when {
			current.lines.isEmpty() -> {
				mutableState.value = current.copy(lineError = required())
				return
			}

			current.lines.any { !Decimals.isValid(it.quantity) } -> {
				mutableState.value = current.copy(lineError = quantityInvalid())
				return
			}

			current.placedTotal?.let { Decimals.same(it, current.remaining) } != true -> {
				mutableState.value = current.copy(lineError = totalMismatch())
				return
			}
		}
		running?.cancel()
		mutableState.value = current.copy(saving = true, lineError = null, error = null)
		running = viewModelScope.launch {
			val bodies = current.lines.map { PlacementBody(it.locationId, it.quantity) }
			mutableState.value = try {
				val saved = session.withToken { token -> receiving.placements(token, target.assignment.id, bodies) }
				mutableState.value.copy(saving = false, saved = saved)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value.copy(saving = false, error = e.message)
			} catch (e: Exception) {
				mutableState.value.copy(saving = false, error = unreachable())
			}
		}
	}

	private suspend fun targetsOf(token: String): List<PlacementTarget> =
		receiving.mine(token)
			.filter { it.invoice.status.code == IN_ASSIGNMENT }
			.flatMap { receipt ->
				receipt.lines.flatMap { line ->
					line.assignments
						.filter { it.countedQuantity != null && it.outcome == MATCH && !it.placed }
						.map { PlacementTarget(it, receipt.invoice.number, receipt.invoice.warehouse.id, line.product, line.unit) }
				}
			}

	// A scanned or typed location code as a location of the invoice's warehouse, then a row for it. A code this
	// warehouse does not hold is refused here, with the API's own message.
	private fun addLocation(value: String) {
		val target = mutableState.value.target ?: return
		if (value.isEmpty()) {
			mutableState.value = mutableState.value.copy(scanError = required())
			return
		}
		running?.cancel()
		mutableState.value = mutableState.value.copy(scanError = null, error = null)
		running = viewModelScope.launch {
			try {
				val found = session.withToken { token -> locations.inWarehouse(token, target.warehouseId, value).filter { it.code.equals(value, ignoreCase = true) } }
				val location = found.firstOrNull()
				val current = mutableState.value
				mutableState.value = when {
					location == null -> current.copy(scanError = notInWarehouse())
					// The same location twice in one placement has no meaning.
					current.lines.any { it.locationId == location.id } -> current.copy(scan = "")
					else -> current.copy(
						scan = "",
						lines = current.lines + PlacementLine(location.id, location.code, current.unassigned ?: ""),
					)
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

	private companion object {
		// The invoice status whose assignments are counted and settled, so they are ready to place
		// (docs/modules/material-receiving.md sections 6 to 8).
		const val IN_ASSIGNMENT = "IN_ASSIGNMENT"

		// Counted and equal to what was expected: a match, or a discrepancy that was adjusted.
		const val MATCH = "MATCH"
	}
}
