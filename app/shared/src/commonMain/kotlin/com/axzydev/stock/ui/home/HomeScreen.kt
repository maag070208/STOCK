package com.axzydev.stock.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.health.HealthClient
import com.axzydev.stock.data.health.HealthState
import com.axzydev.stock.data.session.SessionUser
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.AppIcon
import com.axzydev.stock.ui.components.AppText
import com.axzydev.stock.ui.components.Card
import com.axzydev.stock.ui.components.HeroBar
import com.axzydev.stock.ui.components.HeroHeadline
import com.axzydev.stock.ui.components.HeroIconButton
import com.axzydev.stock.ui.components.HeroScreen
import com.axzydev.stock.ui.components.ModuleButton
import com.axzydev.stock.ui.components.StatusBadge
import com.axzydev.stock.ui.components.StatusTone
import com.axzydev.stock.ui.nav.Module
import com.axzydev.stock.ui.nav.ModuleEntry
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The first screen after signing in: the hero greets the person and holds the sign-out; the sheet is the hub, a
// button per module the person may use, and the state of the API. Each module opens its own menu of pages.
@Composable
fun HomeScreen(client: HealthClient, user: SessionUser, modules: List<ModuleEntry>, onOpenModule: (Module) -> Unit, onSignOut: () -> Unit) {
	var state by remember { mutableStateOf<HealthState?>(null) }
	LaunchedEffect(client) { state = client.check() }
	HeroScreen(
		hero = {
			HeroBar { HeroIconButton(AppIcon.SIGN_OUT, text(Text.SIGN_OUT), onSignOut, tag = "sign-out") }
			HeroHeadline(text(Text.GREETING).replace("{name}", "*${user.name.replace("*", "")}*"), Modifier.padding(top = DesignTokens.Spacing.lg.dp))
			AppText(text(Text.HOME_PROMPT), StockFlowType.bodyLg, StockFlow.colors.onPrimary)
		},
	) {
		AppText(text(Text.MODULES_LABEL).uppercase(), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted, Modifier.testTag("modules-label"))
		AppText(text(Text.MODULES_HINT), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		// Two modules to a row, so the hub is a small grid of buttons.
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
				// Keeps the last button of an odd row the same width as the others.
				if (pair.size == 1) Spacer(Modifier.weight(1f))
			}
		}
		Card {
			// Operational labels are small, uppercase and widely tracked.
			AppText(
				text = text(Text.HEALTH_LABEL).uppercase(),
				style = StockFlowType.labelSm,
				color = StockFlow.colors.onSurfaceMuted,
			)
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

// Gray while checking, green when the API answers, yellow when it does not.
internal fun healthTone(state: HealthState?): StatusTone =
	when (state) {
		null -> StatusTone.PLANNED
		HealthState.Up -> StatusTone.PRODUCTION
		HealthState.Down, HealthState.Unreachable -> StatusTone.MOCK
	}
