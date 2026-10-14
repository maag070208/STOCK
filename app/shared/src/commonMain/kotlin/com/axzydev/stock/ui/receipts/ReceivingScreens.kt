package com.axzydev.stock.ui.receipts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.axzydev.stock.data.receipts.LineView
import com.axzydev.stock.data.receipts.MyReceipt
import com.axzydev.stock.feature.receipts.DiscrepanciesViewModel
import com.axzydev.stock.feature.receipts.ReceiptsViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// What the operator has to receive: their invoices, each with their own lines and assignments
// (docs/modules/material-receiving.md).
@Composable
fun MyReceiptsScreen(receipts: ReceiptsViewModel, onBack: () -> Unit) {
	val state by receipts.state.collectAsState()
	LaunchedEffect(receipts) { receipts.load() }
	PageScaffold(
		title = text(Text.PAGE_MY_RECEIPTS),
		subtitle = text(Text.PAGE_MY_RECEIPTS_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "receipts-back",
	) {
		RemoteList(state, receipts::load, text(Text.EMPTY), "receipts") { receipt ->
			ReceiptCard(receipt, tag = "receipt-${receipt.invoice.id}")
		}
	}
}

@Composable
private fun ReceiptCard(receipt: MyReceipt, tag: String) {
	val invoice = receipt.invoice
	Card(Modifier.testTag(tag)) {
		AppText(invoice.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			listOfNotNull(
				invoice.supplier.name ?: invoice.supplier.code,
				invoice.warehouse.code,
				invoice.status.label ?: invoice.status.code,
			).joinToString(" · "),
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 2,
		)
		receipt.lines.forEach { line ->
			AppText(lineTitle(line), StockFlowType.bodyLg, StockFlow.colors.onSurface, maxLines = 2)
			line.assignments.forEach { assignment ->
				val decimals = line.unit?.decimalPlaces
				val counted = assignment.countedQuantity?.let { formatQuantity(it, decimals) } ?: "—"
				AppText(
					listOf(
						"${text(Text.EXPECTED)} ${formatQuantity(assignment.expectedQuantity, decimals)}",
						"${text(Text.COUNTED)} $counted",
						"${text(Text.PLACED)} ${formatQuantity(assignment.placedQuantity, decimals)}",
					).joinToString(" · "),
					StockFlowType.bodyMd,
					StockFlow.colors.onSurfaceMuted,
					maxLines = 2,
				)
			}
		}
	}
}

private fun lineTitle(line: LineView): String = listOfNotNull(line.product.code, line.product.name).joinToString(" · ")

// The open differences a reader may resolve: the expected and the counted quantity, and by how much they differ.
@Composable
fun DiscrepanciesScreen(discrepancies: DiscrepanciesViewModel, onBack: () -> Unit) {
	val state by discrepancies.state.collectAsState()
	LaunchedEffect(discrepancies) { discrepancies.load() }
	PageScaffold(
		title = text(Text.PAGE_DISCREPANCIES),
		subtitle = text(Text.PAGE_DISCREPANCIES_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "discrepancies-back",
	) {
		RemoteList(state, discrepancies::load, text(Text.EMPTY), "discrepancies") { row ->
			DataRow(
				title = listOfNotNull(row.product.code, row.product.name).joinToString(" · "),
				tag = "discrepancy-${row.assignmentId}",
				subtitle = listOfNotNull(row.invoice.code, row.invoice.name, row.warehouse.code).joinToString(" · "),
				trailing = row.difference,
			)
		}
	}
}
