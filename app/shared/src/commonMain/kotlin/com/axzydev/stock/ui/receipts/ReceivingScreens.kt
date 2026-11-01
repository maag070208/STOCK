package com.axzydev.stock.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.receipts.AssignmentView
import com.axzydev.stock.data.receipts.LineView
import com.axzydev.stock.data.receipts.MyReceipt
import com.axzydev.stock.feature.receipts.CountTarget
import com.axzydev.stock.feature.receipts.DiscrepanciesViewModel
import com.axzydev.stock.feature.receipts.PlacementTarget
import com.axzydev.stock.feature.receipts.ReceiptsViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoProgressBar
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.DataRow
import com.axzydev.stock.ui.components.Icon
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.RemoteList
import com.axzydev.stock.ui.components.StatusBadge
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// What the operator has to receive: their invoices with how far each one is, and each material with its state. Touching
// a material takes them straight to what it needs next (docs/modules/material-receiving.md).
@Composable
fun MyReceiptsScreen(receipts: ReceiptsViewModel, onCount: (CountTarget) -> Unit, onPlace: (PlacementTarget) -> Unit, onBack: () -> Unit) {
	val state by receipts.state.collectAsState()
	LaunchedEffect(receipts) { receipts.load() }
	PageScaffold(
		title = text(Text.PAGE_MY_RECEIPTS),
		subtitle = text(Text.PAGE_MY_RECEIPTS_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK_MODULES),
		backTag = "receipts-back",
	) {
		RemoteList(state, receipts::load, text(Text.EMPTY_RECEIPTS), "receipts", emptyHint = text(Text.EMPTY_HINT)) { receipt ->
			ReceiptCard(receipt, "receipt-${receipt.invoice.id}", onCount, onPlace)
		}
	}
}

@Composable
private fun ReceiptCard(receipt: MyReceipt, tag: String, onCount: (CountTarget) -> Unit, onPlace: (PlacementTarget) -> Unit) {
	val invoice = receipt.invoice
	val assignments = receipt.lines.flatMap { it.assignments }
	val placed = assignments.count { it.placed }
	Card(Modifier.testTag(tag)) {
		AppText(invoice.number, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(
			listOfNotNull(invoice.supplier.name ?: invoice.supplier.code, invoice.warehouse.code).joinToString(" · "),
			StockFlowType.bodyMd,
			StockFlow.colors.onSurfaceMuted,
			maxLines = 2,
		)
		BentoProgressBar(if (assignments.isEmpty()) 0f else placed.toFloat() / assignments.size)
		AppText(
			text(Text.OF_PROGRESS).replace("{done}", placed.toString()).replace("{total}", assignments.size.toString()),
			StockFlowType.labelSm,
			StockFlow.colors.onSurfaceMuted,
		)
		receipt.lines.forEach { line ->
			line.assignments.forEach { assignment ->
				val open = {
					when {
						assignment.countedQuantity == null -> onCount(CountTarget(assignment, invoice.number, invoice.warehouse.id, line.product, line.unit))
						assignment.readyToPlace && !assignment.placed -> onPlace(PlacementTarget(assignment, invoice.number, invoice.warehouse.id, line.product, line.unit))
					}
				}
				AssignmentRow(line, assignment, open)
			}
		}
	}
}

// One material of the invoice: what it is, how much, and the state that says what comes next.
@Composable
private fun AssignmentRow(line: LineView, assignment: AssignmentView, onClick: () -> Unit) {
	val (label, tone) = when {
		assignment.placed -> Text.STATE_PLACED to StatusTone.PRODUCTION
		assignment.countedQuantity == null -> Text.STATE_TO_COUNT to StatusTone.PLANNED
		assignment.readyToPlace -> Text.STATE_TO_PLACE to StatusTone.DEVELOPMENT
		else -> Text.STATE_DIFFERENCE to StatusTone.MOCK
	}
	val actionable = !assignment.placed && (assignment.countedQuantity == null || assignment.readyToPlace)
	Row(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceSoft, RoundedCornerShape(AppBento.fieldCorner))
			.then(if (actionable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
			.padding(DesignTokens.Spacing.md.dp)
			.testTag("assignment-${assignment.id}"),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			AppText(lineTitle(line), StockFlowType.labelMd, StockFlow.colors.onSurface, maxLines = 2)
			AppText(formatQuantity(assignment.expectedQuantity, line.unit?.decimalPlaces) + (line.unit?.label?.let { " $it" } ?: ""), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted, maxLines = 1)
		}
		Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
		StatusBadge(text(label), tone)
		if (actionable) {
			Spacer(Modifier.width(DesignTokens.Spacing.xs.dp))
			Icon(AppIcon.CHEVRON, StockFlow.colors.onSurfaceMuted, size = 16.dp)
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
		RemoteList(state, discrepancies::load, text(Text.EMPTY_DISCREPANCIES), "discrepancies", emptyHint = text(Text.EMPTY_HINT)) { row ->
			DataRow(
				title = listOfNotNull(row.product.code, row.product.name).joinToString(" · "),
				tag = "discrepancy-${row.assignmentId}",
				subtitle = listOfNotNull(row.invoice.code, row.invoice.name, row.warehouse.code).joinToString(" · "),
				trailing = row.difference,
			)
		}
	}
}
