package com.axzydev.stock.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.data.Decimals
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
			AppText(title, if (onBack != null) StockFlowType.headlineLg else StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 1)
			if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
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
 * surface; the value and the caption use the tone's ink, never white on a pastel. [waveLevel] puts moving water behind
 * the text, as full as the share it stands for.
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
	waveLevel: Float? = null,
	waveColor: Color? = null,
	onClick: (() -> Unit)? = null,
) {
	val colors = AppBento.tone(tone)
	val shape = RoundedCornerShape(AppBento.tile)
	Box(
		modifier
			.fillMaxWidth()
			.heightIn(min = 148.dp)
			.height(IntrinsicSize.Min)
			.clip(shape)
			.background(colors.container)
			.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
			.testTag(tag),
	) {
		if (waveLevel != null) BentoWave(waveLevel, waveColor ?: colors.content, Modifier.matchParentSize())
		Column(
			Modifier.fillMaxSize().padding(DesignTokens.Spacing.lg.dp),
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
}

/** The progress of the shift: a gauge beside what it counts. */
@Composable
fun BentoProgressTile(label: String, value: String, caption: String, fraction: Float, tag: String, modifier: Modifier = Modifier) {
	Row(
		modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, RoundedCornerShape(AppBento.tile))
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag(tag),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp),
	) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
			AppText(label, StockFlowType.labelMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
			AppText(value, StockFlowType.headlineXl, StockFlow.colors.onSurface, maxLines = 1)
			AppText(caption, StockFlowType.labelSm, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
		BentoGauge(fraction, StockFlow.colors.primary, StockFlow.colors.surfaceSoft, Modifier.width(132.dp), height = 72.dp)
	}
}

/** A place of the bottom bar: a round icon, ink when it is the current one. */
data class BentoNavItem(val icon: AppIcon, val label: String, val selected: Boolean, val tag: String, val onClick: () -> Unit)

/** The way around the app: one floating white pill over the canvas, within reach of the thumb. */
@Composable
fun BentoBottomBar(items: List<BentoNavItem>) {
	Row(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.background)
			.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
			.padding(horizontal = DesignTokens.Spacing.lg.dp, vertical = DesignTokens.Spacing.sm.dp)
			.background(StockFlow.colors.surfaceElevated, AppBento.pill)
			.padding(DesignTokens.Spacing.sm.dp),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically,
	) {
		items.forEach { item ->
			val fill by animateColorAsState(if (item.selected) AppBento.ink() else StockFlow.colors.surfaceElevated)
			val ink by animateColorAsState(if (item.selected) AppBento.onInk() else StockFlow.colors.onSurfaceMuted)
			Box(
				Modifier
					.size(44.dp)
					.clip(CircleShape)
					.background(fill)
					.clickable(role = Role.Tab, onClick = item.onClick)
					.semantics {
						contentDescription = item.label
						selected = item.selected
					}
					.testTag(item.tag),
				contentAlignment = Alignment.Center,
			) {
				Icon(item.icon, ink, size = 22.dp)
			}
		}
	}
}

/** The steps of a task: the current one in the accent, the ones behind it with their check. */
@Composable
fun BentoSteps(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
	Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		steps.forEachIndexed { index, step ->
			val now = index == current
			val done = index < current
			Row(
				Modifier
					.weight(1f)
					.background(if (now) StockFlow.colors.primary else StockFlow.colors.surfaceElevated, AppBento.pill)
					.padding(vertical = DesignTokens.Spacing.sm.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (done) {
					Icon(AppIcon.CHECK, StockFlow.colors.primary, size = 14.dp)
					Spacer(Modifier.width(DesignTokens.Spacing.xs.dp))
				}
				AppText(step, StockFlowType.labelSm, if (now) StockFlow.colors.onPrimary else StockFlow.colors.onSurfaceMuted, maxLines = 1)
			}
		}
	}
}

/** A small choice: one touch fills in the common answer. */
@Composable
fun BentoChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
	Box(
		Modifier
			.clip(AppBento.pill)
			.background(if (selected) AppBento.ink() else StockFlow.colors.surfaceElevated)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = DesignTokens.Spacing.md.dp, vertical = DesignTokens.Spacing.sm.dp)
			.testTag(tag),
	) {
		AppText(label, StockFlowType.labelSm, if (selected) AppBento.onInk() else StockFlow.colors.onSurface, maxLines = 1)
	}
}

/** A white row to choose: a title with its lines, a detail at the right, and a chevron saying it opens. */
@Composable
fun BentoChoiceRow(title: String, tag: String, onClick: () -> Unit, subtitle: String? = null, detail: String? = null, tone: BentoTone = BentoTone.Orange, icon: AppIcon? = null) {
	val colors = AppBento.tone(tone)
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(AppBento.tile))
			.background(StockFlow.colors.surfaceElevated)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag(tag),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp),
	) {
		if (icon != null) {
			Box(Modifier.size(40.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
				Icon(icon, colors.content, size = 20.dp)
			}
		}
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp)) {
			AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
			if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 2)
		}
		if (detail != null) {
			Box(Modifier.background(colors.container, AppBento.pill).padding(horizontal = DesignTokens.Spacing.md.dp, vertical = DesignTokens.Spacing.xs.dp)) {
				AppText(detail, StockFlowType.labelSm, colors.content, maxLines = 1)
			}
		}
		Icon(AppIcon.CHEVRON, StockFlow.colors.onSurfaceMuted, size = 18.dp)
	}
}

/** What is being worked on, in big: the material, its context, and the number that matters. */
@Composable
fun BentoHero(title: String, value: String, caption: String, tag: String, subtitle: String? = null) {
	val colors = AppBento.tone(BentoTone.Ink)
	Column(
		Modifier
			.fillMaxWidth()
			.background(colors.container, RoundedCornerShape(AppBento.tile))
			.padding(DesignTokens.Spacing.lg.dp)
			.testTag(tag),
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.dp),
	) {
		AppText(title, StockFlowType.titleMd, colors.content, maxLines = 2)
		if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd, colors.content.copy(alpha = 0.7f), maxLines = 2)
		Spacer(Modifier.height(DesignTokens.Spacing.sm.dp))
		AppText(caption.uppercase(), StockFlowType.labelSm, colors.content.copy(alpha = 0.7f), maxLines = 1)
		AppText(value, StockFlowType.headlineXl, StockFlow.colors.primary, maxLines = 1)
	}
}

/** A quantity for hands in gloves: the field, with a big minus and a big plus beside it. */
@Composable
fun BentoQuantity(label: String, value: String, onChange: (String) -> Unit, tag: String, error: String? = null, onDone: () -> Unit = {}) {
	val step = { up: Boolean ->
		val current = value.trim().ifEmpty { "0" }
		val next = if (up) Decimals.add(current, "1") else if (Decimals.above(current, "1")) Decimals.subtract(current, "1") else "0"
		if (next != null) onChange(Decimals.plain(next))
	}
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		StepButton("−", label, "$tag-minus") { step(false) }
		TextInput(label = label, value = value, onValueChange = onChange, tag = tag, modifier = Modifier.weight(1f), numeric = true, error = error, imeAction = ImeAction.Done, onDone = onDone)
		StepButton("+", label, "$tag-plus") { step(true) }
	}
}

@Composable
private fun StepButton(sign: String, label: String, tag: String, onClick: () -> Unit) {
	Box(
		Modifier
			.size(AppBento.fieldHeight)
			.clip(CircleShape)
			.background(StockFlow.colors.surfaceElevated)
			.clickable(role = Role.Button, onClick = onClick)
			.semantics { contentDescription = "$sign $label" }
			.testTag(tag),
		contentAlignment = Alignment.Center,
	) {
		AppText(sign, StockFlowType.headlineLg, StockFlow.colors.primary, maxLines = 1)
	}
}

/** The end of a task: what happened, big, so it is read at a glance. */
@Composable
fun BentoResult(title: String, tag: String, success: Boolean = true, content: @Composable ColumnScope.() -> Unit = {}) {
	val color = if (success) StockFlow.colors.success else StockFlow.colors.warning
	Column(
		Modifier
			.fillMaxWidth()
			.background(StockFlow.colors.surfaceElevated, RoundedCornerShape(AppBento.card))
			.padding(DesignTokens.Spacing.xl.dp)
			.testTag(tag),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp),
	) {
		Box(Modifier.size(64.dp).background(color.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
			Icon(if (success) AppIcon.CHECK else AppIcon.ALERT, color, size = 30.dp)
		}
		AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 3)
		content()
	}
}

/** How far along something is: a slim bar in the accent. */
@Composable
fun BentoProgressBar(fraction: Float, modifier: Modifier = Modifier) {
	Box(modifier.fillMaxWidth().height(6.dp).clip(AppBento.pill).background(StockFlow.colors.surfaceSoft)) {
		Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(AppBento.pill).background(StockFlow.colors.primary))
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
