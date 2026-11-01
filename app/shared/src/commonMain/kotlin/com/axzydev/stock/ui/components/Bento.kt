package com.axzydev.stock.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The bento shell of the app (design/stockflow-DESIGN.md): a soft gray canvas, a top bar of round white controls and a
// big title, and white cards below. There is no hero: the screen starts with the work.

/** A screen: the canvas, the safe insets, the scroll and the padding of the design. */
@Composable
fun BentoScreen(modifier: Modifier = Modifier, scrollState: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier
			.fillMaxSize()
			.background(StockFlow.colors.background)
			.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
			.verticalScroll(scrollState)
			.imePadding()
			.padding(horizontal = DesignTokens.Spacing.lg.dp)
			.padding(top = DesignTokens.Spacing.md.dp, bottom = DesignTokens.Spacing.xl3.dp),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp),
		content = content,
	)
}

/** The line of a screen: the round back control, a small title with its line, and the screen's actions. */
@Composable
fun BentoTopBar(
	title: String,
	subtitle: String? = null,
	onBack: (() -> Unit)? = null,
	backLabel: String = "",
	backTag: String = "",
	actions: @Composable RowScope.() -> Unit = {},
) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		if (onBack != null) {
			BentoIconButton(AppIcon.BACK, backLabel, onBack, tag = backTag)
			Spacer(Modifier.width(DesignTokens.Spacing.md.dp))
		}
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
			if (subtitle != null) AppText(subtitle, StockFlowType.bodySm, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
		actions()
	}
}

/** The big title of a screen, under the top bar. */
@Composable
fun BentoPageHeader(title: String, subtitle: String? = null) {
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		AppText(title, StockFlowType.headlineLg, StockFlow.colors.onBackground, maxLines = 2)
		if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 3)
	}
}

/** A round white control of the top bar: back, sign out, or the action of the screen. */
@Composable
fun BentoIconButton(icon: AppIcon, label: String, onClick: () -> Unit, tag: String) {
	Box(
		Modifier
			.size(AppBento.topBarButton)
			.background(StockFlow.colors.surfaceElevated, CircleShape)
			.clickable(role = Role.Button, onClick = onClick)
			.semantics { contentDescription = label }
			.testTag(tag),
		contentAlignment = Alignment.Center,
	) {
		Icon(icon, AppBento.ink(), size = 20.dp)
	}
}

/**
 * A tile of the dashboard: the label with its icon, a big tabular number and the caption under it. The tone paints the
 * surface; the value and the caption use the tone's ink, never white on a pastel.
 */
@Composable
fun BentoTile(
	label: String,
	value: String,
	tag: String,
	modifier: Modifier = Modifier,
	caption: String? = null,
	tone: BentoTone = BentoTone.Plain,
	icon: AppIcon? = null,
	onClick: (() -> Unit)? = null,
) {
	val colors = AppBento.tone(tone)
	Column(
		modifier
			.fillMaxWidth()
			.heightIn(min = 128.dp)
			.background(colors.container, RoundedCornerShape(AppBento.tile))
			.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.SpaceBetween,
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			AppText(label, StockFlowType.labelMd, colors.content.copy(alpha = 0.85f), Modifier.weight(1f), maxLines = 2)
			if (icon != null) Icon(icon, colors.content.copy(alpha = 0.85f), size = 20.dp)
		}
		Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
			AppText(value, StockFlowType.headlineXl, colors.content, maxLines = 1)
			if (caption != null) AppText(caption, StockFlowType.labelSm, colors.content.copy(alpha = 0.75f), maxLines = 2)
		}
	}
}

/** A small white counter: what a list holds and what the count is of. */
@Composable
fun BentoStat(label: String, value: String, dot: Color, tag: String, modifier: Modifier = Modifier) {
	Column(
		modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, RoundedCornerShape(AppBento.tile))
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.md.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp),
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Box(Modifier.size(8.dp).background(dot, CircleShape))
			Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
			AppText(label, StockFlowType.labelMd, StockFlow.colors.onSurfaceMuted, maxLines = 1)
		}
		AppText(value, StockFlowType.headlineLg, StockFlow.colors.onSurface, maxLines = 1)
	}
}

/** The overline of a section, in the design's muted small caps. */
@Composable
fun BentoLabel(text: String, modifier: Modifier = Modifier) {
	AppText(text.uppercase(), StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted, modifier)
}

/** A section under a title style, for the screens that need a heading without a page header. */
@Composable
fun BentoSectionTitle(title: String, hint: String? = null) {
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
		AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		if (hint != null) AppText(hint, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
	}
}
