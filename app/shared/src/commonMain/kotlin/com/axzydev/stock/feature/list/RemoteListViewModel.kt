package com.axzydev.stock.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// What a screen that reads a list from the API shows: it is loading, it has rows, it is empty, or it failed.
data class ListState<T>(
	val loading: Boolean = false,
	val rows: List<T> = emptyList(),
	val error: String? = null,
) {
	val empty: Boolean get() = !loading && error == null && rows.isEmpty()
}

// The state of one screen that reads a list: load it with the session, show the API's own message on a refusal, and
// the app's own when there is no connection. Pages of the modules share this.
abstract class RemoteListViewModel<T>(
	private val session: Authorized,
	private val unreachable: () -> String,
) : ViewModel() {

	private val mutableState = MutableStateFlow(ListState<T>())
	val state: StateFlow<ListState<T>> = mutableState.asStateFlow()

	// One call of the API. A refusal is an ApiError; anything else is a lost connection.
	protected abstract suspend fun fetch(token: String): List<T>

	fun load() {
		mutableState.value = ListState(loading = true)
		viewModelScope.launch {
			mutableState.value = try {
				ListState(rows = session.withToken { fetch(it) })
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				ListState(error = e.message)
			} catch (e: Exception) {
				ListState(error = unreachable())
			}
		}
	}
}
