package com.axzydev.stock.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import com.axzydev.stock.ui.components.BentoProgressTile
import com.axzydev.stock.ui.components.BentoScreen
import com.axzydev.stock.ui.components.BentoTile
import com.axzydev.stock.ui.components.BentoTopBar
import com.axzydev.stock.ui.components.Icon
import com.axzydev.stock.ui.components.ModuleButton
import com.axzydev.stock.ui.components.StatusBadge
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.components.rememberCountUp
import com.axzydev.stock.ui.components.riseIn
import com.axzydev.stock.ui.nav.Module
import com.axzydev.stock.ui.nav.ModuleEntry
import com.axzydev.stock.ui.nav.ModuleGroup
import com.axzydev.stock.ui.nav.ModulePage
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The home is the dashboard of the shift, not a menu: the greeting, the two tasks of the receipt with how much is
// pending in each, the progress of the shift and the modules by section, with the state of the API under the greeting,
// where it is seen without scrolling. It is the same language
// as the web home: pastel tiles, moving water, a gauge and numbers that climb.
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
	val assignments = work.rows.flatMap { receipt -> receipt.lines.flatMap { it.assignments } }
	val placedCount = assignments.count { it.placed }
	val share = { part: Int -> if (assignments.isEmpty()) 0f else part.toFloat() / assignments.size }

	BentoScreen {
		BentoTopBar(
			title = AppInfo.NAME,
			actions = { BentoIconButton(AppIcon.SIGN_OUT, text(Text.SIGN_OUT), onSignOut, tag = "sign-out") },
		)
		BentoPageHeader(
			text(Text.GREETING).replace("{name}", user.name),
			text(Text.HOME_PROMPT),
		)
		StatusBadge(text = text(healthMessage(state)), tone = healthTone(state))

		// Scanning comes first, here as on every screen: the one big action of the home.
		val scanPage = modules.firstOrNull { it.module.id == "materials" }?.let { it to it.pages.first() }
			?: receiptEntry?.let { entry -> countPage?.let { entry to it } }
		if (scanPage != null) {
			val colors = AppBento.tone(BentoTone.Ink)
			Row(
				Modifier
					.fillMaxWidth()
					.riseIn(0)
					.clip(RoundedCornerShape(AppBento.tile))
					.background(colors.container)
					.clickable(role = Role.Button) { onOpenPage(scanPage.first.module, scanPage.second) }
					.padding(DesignTokens.Spacing.lg.dp)
					.testTag("home-scan"),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp),
			) {
				Box(Modifier.size(52.dp).background(StockFlow.colors.primary, CircleShape), contentAlignment = Alignment.Center) {
					Icon(AppIcon.SCAN, StockFlow.colors.onPrimary, size = 26.dp)
				}
				Column(Modifier.weight(1f)) {
					AppText(text(Text.HOME_SCAN), StockFlowType.titleMd, colors.content, maxLines = 1)
					AppText(text(Text.HOME_SCAN_HINT), StockFlowType.bodyMd, colors.content.copy(alpha = 0.7f), maxLines = 2)
				}
			}
		}

		// The work of the receipt, side by side: each tile opens the task it counts, and its water is its share of the shift.
		if (countPage != null || placementPage != null) {
			Row(Modifier.fillMaxWidth().riseIn(1), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp)) {
				if (countPage != null) {
					BentoTile(
						label = text(Text.PAGE_COUNT),
						value = rememberCountUp(pendingCount).toString(),
						caption = text(Text.PAGE_COUNT_HINT),
						tone = BentoTone.Orange,
						icon = AppIcon.SCAN,
						waveLevel = share(pendingCount),
						waveColor = StockFlow.colors.primary,
						onClick = { onOpenPage(receiptEntry!!.module, countPage) },
						tag = "task-count",
						modifier = Modifier.weight(1f),
					)
				}
				if (placementPage != null) {
					BentoTile(
						label = text(Text.PAGE_PLACEMENT),
						value = rememberCountUp(placementCount).toString(),
						caption = text(Text.PAGE_PLACEMENT_HINT),
						tone = BentoTone.Ink,
						icon = AppIcon.WAREHOUSE,
						waveLevel = share(placementCount),
						onClick = { onOpenPage(receiptEntry!!.module, placementPage) },
						tag = "task-placement",
						modifier = Modifier.weight(1f),
					)
				}
			}
			BentoProgressTile(
				label = text(Text.SHIFT_PROGRESS),
				value = rememberCountUp(placedCount).toString(),
				caption = if (assignments.isEmpty()) text(Text.SHIFT_EMPTY) else text(Text.SHIFT_PROGRESS_HINT).replace("{total}", assignments.size.toString()),
				fraction = share(placedCount),
				tag = "shift-progress",
				modifier = Modifier.riseIn(2),
			)
		}

		// The modules the person may use, section by section, as on the web's menu.
		ModuleGroup.entries.forEachIndexed { index, group ->
			val members = modules.filter { it.module.group == group }
			if (members.isNotEmpty()) {
				BentoLabel(text(group.title), Modifier.testTag(if (index == 0) "modules-label" else "modules-label-${group.name.lowercase()}").riseIn(3 + index))
				members.chunked(2).forEach { pair ->
					Row(Modifier.fillMaxWidth().riseIn(3 + index), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp)) {
						pair.forEach { entry ->
							ModuleButton(
								icon = entry.module.icon,
								title = text(entry.module.title),
								hint = text(entry.module.hint),
								onClick = { onOpenModule(entry.module) },
								tag = "module-${entry.module.id}",
								tone = entry.module.tone,
								comingSoon = entry.comingSoon,
								modifier = Modifier.weight(1f),
							)
						}
						if (pair.size == 1) Spacer(Modifier.weight(1f))
					}
				}
			}
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
