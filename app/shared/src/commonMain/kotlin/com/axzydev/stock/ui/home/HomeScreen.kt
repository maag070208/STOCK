package com.axzydev.stock.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.axzydev.stock.AppInfo
import com.axzydev.stock.data.health.HealthClient
import com.axzydev.stock.data.health.HealthState
import com.axzydev.stock.data.session.SessionUser
import com.axzydev.stock.feature.receipts.ReceiptsViewModel
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.BentoIconButton
import com.axzydev.stock.ui.components.BentoLabel
import com.axzydev.stock.ui.components.BentoPageHeader
import com.axzydev.stock.ui.components.BentoScreen
import com.axzydev.stock.ui.components.BentoTile
import com.axzydev.stock.ui.components.BentoTopBar
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.ModuleButton
import com.axzydev.stock.ui.components.StatusBadge
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.nav.Module
import com.axzydev.stock.ui.nav.ModuleEntry
import com.axzydev.stock.ui.nav.ModulePage
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The home is the dashboard of the shift, not a menu: the greeting, the two tasks of the receipt with how much is
// pending in each, the modules the person may use, and the state of the API. It is the same layout as the web home.
@Composable
fun HomeScreen(
	client: HealthClient,
	user: SessionUser,
	modules: List<ModuleEntry>,
	receipts: ReceiptsViewModel,
	onOpenModule: (Module) -> Unit,
	onOpenPage: (Module, ModulePage) -> Unit,
	onSignOut: () -> Unit,
) {
	var state by remember { mutableStateOf<HealthState?>(null) }
	val work by receipts.state.collectAsState()
	val receiptEntry = modules.firstOrNull { it.module.id == "receipts" }
	LaunchedEffect(client) { state = client.check() }
	LaunchedEffect(receipts, receiptEntry) { if (receiptEntry != null) receipts.load() }
	val pendingCount = work.rows.sumOf { receipt ->
		receipt.lines.sumOf { line -> line.assignments.count { it.countedQuantity == null } }
	}
	val placementCount = work.rows.sumOf { receipt ->
		receipt.lines.sumOf { line -> line.assignments.count { it.readyToPlace && !it.placed } }
	}
	val countPage = receiptEntry?.pages?.firstOrNull { it.id == "count" }
	val placementPage = receiptEntry?.pages?.firstOrNull { it.id == "placement" }

	BentoScreen {
		BentoTopBar(
			title = AppInfo.NAME,
			actions = { BentoIconButton(AppIcon.SIGN_OUT, text(Text.SIGN_OUT), onSignOut, tag = "sign-out") },
		)
		BentoPageHeader(
			text(Text.GREETING).replace("{name}", user.name),
			text(Text.HOME_PROMPT),
		)

		// The work of the receipt, side by side: each tile opens the task it counts.
		if (countPage != null || placementPage != null) {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp)) {
				if (countPage != null) {
					BentoTile(
						label = text(Text.PAGE_COUNT),
						value = pendingCount.toString(),
						caption = text(Text.PAGE_COUNT_HINT),
						tone = BentoTone.Mint,
						icon = AppIcon.SCAN,
						onClick = { onOpenPage(receiptEntry!!.module, countPage) },
						tag = "task-count",
						modifier = Modifier.weight(1f),
					)
				}
				if (placementPage != null) {
					BentoTile(
						label = text(Text.PAGE_PLACEMENT),
						value = placementCount.toString(),
						caption = text(Text.PAGE_PLACEMENT_HINT),
						tone = BentoTone.Sea,
						icon = AppIcon.WAREHOUSE,
						onClick = { onOpenPage(receiptEntry!!.module, placementPage) },
						tag = "task-placement",
						modifier = Modifier.weight(1f),
					)
				}
			}
		}

		// The modules the person may use, as bento tiles.
		BentoLabel(text(Text.MODULES_LABEL), Modifier.testTag("modules-label"))
		AppText(text(Text.MODULES_HINT), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		modules.chunked(2).forEach { pair ->
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp)) {
				pair.forEach { entry ->
					ModuleButton(
						icon = entry.module.icon,
						title = text(entry.module.title),
						hint = text(entry.module.hint),
						onClick = { onOpenModule(entry.module) },
						tag = "module-${entry.module.id}",
						comingSoon = entry.comingSoon,
						modifier = Modifier.weight(1f),
					)
				}
				if (pair.size == 1) Spacer(Modifier.weight(1f))
			}
		}

		Card {
			BentoLabel(text(Text.HEALTH_LABEL))
			StatusBadge(text = text(healthMessage(state)), tone = healthTone(state))
		}
	}
}

internal fun healthMessage(state: HealthState?): Text =
	when (state) {
		null -> Text.HEALTH_CHECKING
		HealthState.Up -> Text.HEALTH_UP
		HealthState.Down -> Text.HEALTH_DOWN
		HealthState.Unreachable -> Text.HEALTH_UNREACHABLE
	}

internal fun healthTone(state: HealthState?): StatusTone =
	when (state) {
		null -> StatusTone.PLANNED
		HealthState.Up -> StatusTone.PRODUCTION
		HealthState.Down, HealthState.Unreachable -> StatusTone.MOCK
	}
