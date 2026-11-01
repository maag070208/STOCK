package com.axzydev.stock.ui.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.products.LookupProduct
import com.axzydev.stock.feature.lookup.LookupViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoChoiceRow
import com.axzydev.stock.ui.components.BentoHero
import com.axzydev.stock.ui.components.BentoLabel
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.components.ScanSearch
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Finding a material: the camera reads its barcode, or the search box finds it as the person writes its code or name
// (barcode-scanning.md section 3). What was found stands above the camera, which stays ready for the next code. Nothing
// is created.
@Composable
fun LookupScreen(lookup: LookupViewModel, onBack: () -> Unit) {
	val state by lookup.state.collectAsState()
	PageScaffold(
		title = text(Text.LOOKUP_TITLE),
		subtitle = text(Text.LOOKUP_HINT),
		onBack = onBack,
		backLabel = text(Text.BACK),
		backTag = "lookup-back",
	) {
		state.product?.let { ProductCard(it) }
		ScanSearch(
			instruction = text(Text.SCAN_MATERIAL),
			searchLabel = text(Text.LOOKUP_FIELD),
			query = state.query,
			onQueryChange = lookup::edit,
			onScanned = lookup::scanned,
			onSubmit = lookup::submit,
			tag = "lookup-value",
			error = state.fieldError,
			searching = state.searching,
			onSearch = lookup::suggest,
		) {
			state.error?.let { AppText(it, StockFlowType.bodyMd, StockFlow.colors.danger, Modifier.testTag("lookup-error")) }
			if (state.candidates.isNotEmpty()) {
				BentoLabel(text(Text.LOOKUP_CHOOSE).replace("{count}", state.candidates.size.toString()))
				state.candidates.forEach { candidate ->
					BentoChoiceRow(candidate.name, "candidate-${candidate.code}", { lookup.choose(candidate) }, subtitle = candidate.code, icon = AppIcon.BOXES)
				}
			}
		}
	}
}

// The material found: what it is and, in big, how much there is. Without permission to read stock, the API sends none.
@Composable
private fun ProductCard(product: LookupProduct) {
	val unit = product.unit.label ?: product.unit.code
	val stock = product.stock
	if (stock != null) {
		BentoHero(product.name, "${formatQuantity(stock, product.unit.decimalPlaces)} $unit", text(Text.PRODUCT_STOCK), "lookup-product", subtitle = product.code)
	}
	Card(if (stock == null) Modifier.testTag("lookup-product") else Modifier) {
		if (stock == null) AppText(product.name, StockFlowType.titleMd, StockFlow.colors.onSurface)
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp)) {
			Field(text(Text.PRODUCT_CODE), product.code, Modifier.weight(1f))
			Field(text(Text.PRODUCT_UNIT), unit, Modifier.weight(1f))
		}
		Field(text(Text.PRODUCT_BARCODE), product.barcode)
	}
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
	Column(modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		AppText(label.uppercase(), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
		AppText(value, StockFlowType.codeSm, StockFlow.colors.onSurface)
	}
}
