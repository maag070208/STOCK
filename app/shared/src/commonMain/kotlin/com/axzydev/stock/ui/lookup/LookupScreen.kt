package com.axzydev.stock.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.products.LookupProduct
import com.axzydev.stock.feature.lookup.LookupViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.HeroIconButton
import com.axzydev.stock.ui.components.HeroScreen
import com.axzydev.stock.ui.components.PrimaryButton
import com.axzydev.stock.ui.components.ScannerPanel
import com.axzydev.stock.ui.components.TextInput
import com.axzydev.stock.ui.format.formatQuantity
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// Finding a material: the camera reads its barcode, or the person types its code or name (barcode-scanning.md
// section 3). One product is shown; several, found by name, are a list to pick from. Nothing is created.
@Composable
fun LookupScreen(lookup: LookupViewModel, onBack: () -> Unit) {
	val state by lookup.state.collectAsState()
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	// Searching puts the keyboard away, so the result below the field is in view.
	val search = {
		focus.clearFocus()
		keyboard?.hide()
		lookup.submit()
	}
	HeroScreen(
		hero = {
			Row(verticalAlignment = Alignment.CenterVertically) {
				HeroIconButton(AppIcon.BACK, text(Text.BACK), onBack, tag = "lookup-back")
				Spacer(Modifier.width(DesignTokens.Spacing.md.dp))
				AppText(text(Text.LOOKUP_TITLE), StockFlowType.headlineLg, StockFlow.colors.onPrimary, maxLines = 1)
			}
			AppText(text(Text.LOOKUP_HINT), StockFlowType.bodyLg, StockFlow.colors.onPrimary)
		},
	) {
		ScannerPanel(onScanned = lookup::scanned)
		TextInput(
			label = text(Text.LOOKUP_FIELD),
			value = state.query,
			onValueChange = lookup::edit,
			tag = "lookup-value",
			icon = AppIcon.SEARCH,
			error = state.fieldError,
			imeAction = ImeAction.Search,
			onDone = search,
		)
		PrimaryButton(
			text = text(if (state.searching) Text.LOOKUP_SEARCHING else Text.LOOKUP_SEARCH),
			onClick = search,
			tag = "lookup-search",
			busy = state.searching,
		)
		state.error?.let { AppText(it, StockFlowType.bodyMd, StockFlow.colors.danger, Modifier.testTag("lookup-error")) }
		state.product?.let { ProductCard(it) }
		if (state.candidates.isNotEmpty()) {
			AppText(text(Text.LOOKUP_CHOOSE).replace("{count}", state.candidates.size.toString()), StockFlowType.bodyLg, StockFlow.colors.onSurface)
			state.candidates.forEach { candidate -> Candidate(candidate) { lookup.choose(candidate) } }
		}
	}
}

// The camera, or what it needs: the permission to ask for, or why it cannot run here.
@Composable
private fun ProductCard(product: LookupProduct) {
	Card(Modifier.testTag("lookup-product")) {
		AppText(product.name, StockFlowType.titleMd, StockFlow.colors.onSurface)
		Field(text(Text.PRODUCT_CODE), product.code, code = true)
		Field(text(Text.PRODUCT_BARCODE), product.barcode, code = true)
		Field(text(Text.PRODUCT_UNIT), product.unit.label ?: product.unit.code)
		// Without permission to read stock, the API sends none, and none is shown.
		product.stock?.let { Field(text(Text.PRODUCT_STOCK), "${formatQuantity(it, product.unit.decimalPlaces)} ${product.unit.label ?: product.unit.code}") }
	}
}

@Composable
private fun Field(label: String, value: String, code: Boolean = false) {
	Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		AppText(label.uppercase(), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted)
		AppText(value, if (code) StockFlowType.codeSm else StockFlowType.bodyLg, StockFlow.colors.onSurface)
	}
}

// One of several products found by name: its code and name, and a tap picks it.
@Composable
private fun Candidate(product: LookupProduct, onPick: () -> Unit) {
	val shape = RoundedCornerShape(DesignTokens.Rounded.md.dp)
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, shape)
			.border(1.dp, StockFlow.colors.outline, shape)
			.clickable(role = Role.Button, onClick = onPick)
			.padding(DesignTokens.Spacing.md.dp)
			.testTag("candidate-${product.code}"),
		contentAlignment = Alignment.CenterStart,
	) {
		Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
			AppText(product.code, StockFlowType.codeSm, StockFlow.colors.onSurfaceMuted)
			AppText(product.name, StockFlowType.bodyLg, StockFlow.colors.onSurface)
		}
	}
}
