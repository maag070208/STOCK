package com.axzydev.stock.feature.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.Decimals
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.locations.Location
import com.axzydev.stock.data.locations.LocationsApi
import com.axzydev.stock.data.products.ProductApi
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
	// What is written over the work queue, and what a scan of it found out.
	val queueQuery: String = "",
	val queueError: String? = null,
	val target: PlacementTarget? = null,
	// What is still to place for the target: counted minus what is already placed.
	val remaining: String = "",
	val scan: String = "",
	// The locations of the warehouse that answer what was written, to touch one.
	val suggestions: List<Location> = emptyList(),
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
	private val products: ProductApi,
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
	private var operationKey: String? = null
	private var lastScan: String? = null

	fun load() {
		running?.cancel()
		operationKey = null
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
		operationKey = null
		mutableState.value = PlacementState(targets = emptyList(), target = target, remaining = remainingOf(target.assignment))
	}

	fun back() {
		lastScan = null
		operationKey = null
		load()
	}

	fun editQueueQuery(value: String) {
		mutableState.value = mutableState.value.copy(queueQuery = value, queueError = null)
	}

	// The camera read a material over the work queue: its target opens, or the list narrows to its targets.
	fun scannedMaterial(value: String) {
		val clean = value.trim()
		if (clean.isEmpty() || clean == lastScan) return
		lastScan = clean
		findTarget(clean)
	}

	fun submitQueueQuery() = findTarget(mutableState.value.queueQuery.trim())

	private fun findTarget(value: String) {
		if (value.isEmpty()) return
		val targets = mutableState.value.targets
		running?.cancel()
		running = viewModelScope.launch {
			try {
				val found = session.withToken { token -> products.lookup(token, value).products }
				val matching = targets.filter { target -> found.any { it.id == target.product.id } }
				when (matching.size) {
					0 -> mutableState.value = mutableState.value.copy(queueQuery = value, queueError = notInWarehouse())
					1 -> open(matching.single())
					else -> mutableState.value = mutableState.value.copy(queueQuery = found.first().code, queueError = null)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value = mutableState.value.copy(queueError = e.message)
			} catch (e: Exception) {
				mutableState.value = mutableState.value.copy(queueError = unreachable())
			}
		}
	}

	// What was written so far: the locations of this warehouse with that in their code, to touch one.
	fun suggest() {
		val current = mutableState.value
		val target = current.target ?: return
		val value = current.scan.trim()
		if (value.isEmpty()) return
		running?.cancel()
		running = viewModelScope.launch {
			try {
				val found = session.withToken { token -> locations.inWarehouse(token, target.warehouseId, value) }
				mutableState.value = mutableState.value.copy(suggestions = found.take(SUGGESTIONS))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				mutableState.value = mutableState.value.copy(suggestions = emptyList())
			}
		}
	}

	fun chooseLocation(location: Location) {
		val current = mutableState.value
		mutableState.value = if (current.lines.any { it.locationId == location.id }) {
			current.copy(scan = "", suggestions = emptyList())
		} else {
			current.copy(scan = "", suggestions = emptyList(), scanError = null, lines = current.lines + PlacementLine(location.id, location.code, current.unassigned ?: ""))
		}
	}

	fun clear() {
		running?.cancel()
		lastScan = null
		operationKey = null
		mutableState.value = PlacementState()
	}

	fun editScan(value: String) {
		mutableState.value = mutableState.value.copy(scan = value, scanError = null, suggestions = if (value.isBlank()) emptyList() else mutableState.value.suggestions)
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

	// Any positive part of the remaining quantity can be saved; only empty, zero or excessive placements are refused.
	fun save() {
		val current = mutableState.value
		val target = current.target ?: return
		when {
			current.lines.isEmpty() -> {
				mutableState.value = current.copy(lineError = required())
				return
			}

			current.lines.any { !Decimals.isValid(it.quantity) || Decimals.same(it.quantity, "0") } -> {
				mutableState.value = current.copy(lineError = quantityInvalid())
				return
			}

			current.placedTotal == null || Decimals.above(current.placedTotal!!, current.remaining) -> {
				mutableState.value = current.copy(lineError = totalMismatch())
				return
			}
		}
		val key = operationKey ?: com.axzydev.stock.data.api.newOperationKey().also { operationKey = it }
		running?.cancel()
		mutableState.value = current.copy(saving = true, lineError = null, error = null)
		running = viewModelScope.launch {
			val bodies = current.lines.map { PlacementBody(it.locationId, it.quantity) }
			mutableState.value = try {
				val saved = session.withToken { token -> receiving.placements(token, target.assignment.id, bodies, key) }
				mutableState.value.copy(saving = false, target = target.copy(assignment = saved), remaining = remainingOf(saved), saved = saved)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				mutableState.value.copy(saving = false, error = e.message)
			} catch (e: Exception) {
				mutableState.value.copy(saving = false, error = unreachable())
			}
		}
	}

	fun continuePlacement() {
		val current = mutableState.value
		val saved = current.saved ?: return
		val target = current.target ?: return
		operationKey = null
		lastScan = null
		mutableState.value = current.copy(
			target = target.copy(assignment = saved),
			remaining = remainingOf(saved),
			scan = "",
			lines = emptyList(),
			error = null,
			scanError = null,
			lineError = null,
			saving = false,
			saved = null,
		)
	}

	private fun remainingOf(assignment: com.axzydev.stock.data.receipts.AssignmentView): String =
		assignment.remainingQuantity
			?: Decimals.subtract(assignment.acceptedQuantity ?: assignment.countedQuantity.orEmpty(), assignment.placedQuantity)
			.orEmpty()

	private suspend fun targetsOf(token: String): List<PlacementTarget> =
		receiving.mine(token)
			.filter { it.invoice.status.code in ACTIVE }
			.flatMap { receipt ->
				receipt.lines.flatMap { line ->
					line.assignments
						.filter { it.readyToPlace && !it.placed }
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
						suggestions = emptyList(),
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
		val ACTIVE = setOf("PENDING", "IN_ASSIGNMENT")
		const val SUGGESTIONS = 8
	}
}
