package com.axzydev.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The home is a hub of modules, and each module is a menu of pages. Both are the quiet panel of `Card`: a thin border,
// the design's corners, and no shadow.

// The height a module button keeps, so the buttons of the home line up whatever their text.
private val MODULE_HEIGHT = 140.dp

private val panelShape = RoundedCornerShape(AppBento.tile)

// A module on the home: its icon, its name and one line about what it holds. `comingSoon` marks a module the API
// does not serve yet.
@Composable
fun ModuleButton(icon: AppIcon, title: String, hint: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier, tone: BentoTone = BentoTone.Plain, comingSoon: Boolean = false) {
	val shape = panelShape
	val colors = AppBento.tone(tone)
	Column(
		modifier
			.fillMaxWidth()
			.height(MODULE_HEIGHT)
			.background(StockFlow.colors.surfaceElevated, shape)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp),
	) {
		Box(Modifier.size(40.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
			Icon(icon, colors.content, size = 20.dp)
		}
		AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
		AppText(hint, StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		if (comingSoon) {
			Spacer(Modifier.weight(1f))
			StatusBadge(text(Text.COMING_SOON), StatusTone.PLANNED, Modifier.testTag("$tag-badge"))
		}
	}
}

// A page of a module: a line with its icon, its name, a line under it, and a chevron saying it opens. A page that is
// not built yet (`enabled = false`) keeps its place in the menu, but says so and does not open.
@Composable
fun PageRow(icon: AppIcon, title: String, hint: String, onClick: () -> Unit, tag: String, enabled: Boolean = true) {
	Row(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, panelShape)
			.then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.md.dp)
			.testTag(tag),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(icon, if (enabled) StockFlow.colors.primary else StockFlow.colors.onSurfaceMuted)
		Spacer(Modifier.width(DesignTokens.Spacing.md.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
			AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
			AppText(hint, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
		if (enabled) {
			Icon(AppIcon.CHEVRON, StockFlow.colors.onSurfaceMuted)
		} else {
			StatusBadge(text(Text.COMING_SOON), StatusTone.PLANNED)
		}
	}
}

// One row of a read-only list: a title, an optional line under it, and an optional value at the end.
@Composable
fun DataRow(title: String, tag: String, subtitle: String? = null, trailing: String? = null, badge: Pair<String, StatusTone>? = null) {
	val shape = panelShape
	Column(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, shape)
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.md.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp),
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, Modifier.weight(1f), maxLines = 2)
			if (trailing != null) {
				Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
				AppText(trailing, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
			}
			if (badge != null) {
				Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
				StatusBadge(badge.first, badge.second, Modifier.testTag("$tag-badge"))
			}
		}
		if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 3)
	}
}

// The line the screens show while a list is on its way, when it came back empty, and when it failed.
@Composable
fun ListMessage(text: String, tag: String, error: Boolean = false) {
	AppText(
		text = text,
		style = StockFlowType.bodyMd.copy(textAlign = TextAlign.Center),
		color = if (error) StockFlow.colors.danger else StockFlow.colors.onSurfaceMuted,
		modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.lg.dp).testTag(tag),
	)
}

// A whole page of a module: the back button with the title beside it, so the work starts high on the screen.
@Composable
fun PageScaffold(
	title: String,
	subtitle: String?,
	onBack: () -> Unit,
	backLabel: String,
	backTag: String,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	BentoScreen(modifier = modifier) {
		BentoTopBar(title = title, subtitle = subtitle, onBack = onBack, backLabel = backLabel, backTag = backTag)
		content()
	}
}

// The page of a module the API does not serve yet. It exists so the shape of the app is visible before the phase
// that fills it (docs/phases/F5-orders.md).
@Composable
fun ComingSoon(tag: String) {
	StatusBadge(text(Text.COMING_SOON), StatusTone.PLANNED, Modifier.testTag(tag))
	AppText(text(Text.COMING_SOON_HINT), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
}
