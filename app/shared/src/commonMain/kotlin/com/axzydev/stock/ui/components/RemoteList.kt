package com.axzydev.stock.ui.components

import androidx.compose.runtime.Composable
import com.axzydev.stock.feature.list.ListState
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text

// A list that comes from the API: nothing yet, the rows, empty, or a refusal with a way to try again. Every page of
// the modules that reads a list goes through this, so they all behave the same.
@Composable
fun <T> RemoteList(
	state: ListState<T>,
	onRetry: () -> Unit,
	emptyText: String,
	tag: String,
	row: @Composable (T) -> Unit,
) {
	when {
		state.loading -> ListMessage(text(Text.LOADING), "$tag-loading")
		state.error != null -> {
			ListMessage(state.error, "$tag-error", error = true)
			PrimaryButton(text(Text.RETRY), onRetry, tag = "$tag-retry")
		}
		state.empty -> ListMessage(emptyText, "$tag-empty")
		else -> state.rows.forEach { row(it) }
	}
}
