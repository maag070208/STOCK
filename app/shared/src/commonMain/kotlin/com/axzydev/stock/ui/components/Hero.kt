package com.axzydev.stock.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.axzydev.stock.AppInfo
import com.axzydev.stock.ui.theme.AppLayout
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType
import org.jetbrains.compose.resources.painterResource
import stock_flow.shared.generated.resources.Res
import stock_flow.shared.generated.resources.stockflow_glyph_light

// A screen of the app: the orange hero on top, with the brand and the screen's headline, and the sheet below with
// its content, its rounded top laid over the hero's bottom edge. The hero sits right under the orange status bar
// strip. `art` adds the device card of the reference, the brand on its screen, half hidden by the sheet. The page
// scrolls, so the keyboard never covers a field. Dark mode changes the sheet; the hero is the brand, and stays orange.
@Composable
fun HeroScreen(
	hero: @Composable ColumnScope.() -> Unit,
	modifier: Modifier = Modifier,
	art: Boolean = false,
	content: @Composable ColumnScope.() -> Unit,
) {
	val colors = StockFlow.colors
	val sheet = RoundedCornerShape(topStart = AppLayout.sheetCorner, topEnd = AppLayout.sheetCorner)
	Column(
		modifier
			.fillMaxSize()
			.background(colors.background)
			.verticalScroll(rememberScrollState())
			.imePadding(),
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.background(colors.primary)
				.drawBehind { heroShapes(colors.onPrimary.copy(alpha = DECOR_ALPHA)) }
				.clipToBounds(),
		) {
			Column(
				Modifier
					.fillMaxWidth()
					.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
					.padding(start = DesignTokens.Spacing.xl.dp, end = DesignTokens.Spacing.xl.dp, top = DesignTokens.Spacing.md.dp)
					// The sheet covers the last stretch of the hero.
					.padding(bottom = DesignTokens.Spacing.xl3.dp + AppLayout.sheetCorner + if (art) DEVICE_SHOWN else 0.dp),
				verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp),
				content = hero,
			)
			if (art) DeviceCard(Modifier.align(Alignment.BottomEnd))
		}
		Column(
			Modifier
				.fillMaxWidth()
				.offset(y = -AppLayout.sheetCorner)
				.background(colors.background, sheet)
				.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
				.padding(horizontal = DesignTokens.Spacing.xl.dp, vertical = DesignTokens.Spacing.xl2.dp),
			verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp),
			content = content,
		)
	}
}

// How much of the device card shows above the sheet, and its size.
private val DEVICE_SHOWN = 96.dp
private val DEVICE_WIDTH = 168.dp
private val DEVICE_HEIGHT = 200.dp

// The reference's phone, drawn as a white card with a notch, and the StockFlow glyph on its screen. Its lower part
// is under the sheet.
@Composable
private fun DeviceCard(modifier: Modifier) {
	val corner = RoundedCornerShape(AppLayout.sheetCorner)
	Column(
		modifier
			.padding(end = DesignTokens.Spacing.xl.dp)
			.offset(y = DEVICE_HEIGHT - DEVICE_SHOWN - AppLayout.sheetCorner)
			.size(DEVICE_WIDTH, DEVICE_HEIGHT)
			.background(StockFlow.colors.onPrimary, corner)
			.padding(DesignTokens.Spacing.md.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg.dp),
	) {
		Box(Modifier.size(DEVICE_WIDTH / 3, DesignTokens.Spacing.md.dp + DesignTokens.Spacing.xs.dp).background(StockFlow.colors.onSurface, AppLayout.pill))
		Image(painterResource(Res.drawable.stockflow_glyph_light), contentDescription = null, modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.md.dp))
	}
}

private const val DECOR_ALPHA = 0.16f

// The hero's line drawings, as in the reference: two rings near the corner, and a gear at the bottom left.
private fun DrawScope.heroShapes(color: Color) {
	val line = Stroke(width = 2.dp.toPx())
	val unit = 1.dp.toPx()
	drawCircle(color, radius = 72 * unit, center = Offset(size.width - 8 * unit, 10 * unit), style = line)
	drawCircle(color, radius = 26 * unit, center = Offset(size.width - 64 * unit, size.height * 0.62f), style = line)
	// Above the sheet's edge, so half of it shows.
	gear(color, center = Offset(size.width * 0.12f, size.height - AppLayout.sheetCorner.toPx() - 4 * unit), radius = 34 * unit, line)
}

private fun DrawScope.gear(color: Color, center: Offset, radius: Float, line: Stroke) {
	drawCircle(color, radius = radius, center = center, style = line)
	drawCircle(color, radius = radius * 0.42f, center = center, style = line)
	val tooth = Size(radius * 0.36f, radius * 0.34f)
	repeat(TEETH) { index ->
		rotate(index * 360f / TEETH, pivot = center) {
			drawRoundRect(
				color,
				topLeft = Offset(center.x - tooth.width / 2, center.y - radius - tooth.height + line.width),
				size = tooth,
				cornerRadius = CornerRadius(tooth.width / 4),
				style = line,
			)
		}
	}
}

private const val TEETH = 8

// The hero's first line: the brand on the left, and the screen's actions on the right.
@Composable
fun HeroBar(actions: @Composable RowScope.() -> Unit = {}) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		BrandTile()
		Spacer(Modifier.width(DesignTokens.Spacing.sm.dp))
		AppText(AppInfo.NAME, StockFlowType.titleMd, StockFlow.colors.onPrimary, Modifier.weight(1f), maxLines = 1)
		actions()
	}
}

// The glyph on a white tile, so its orange box shows on the orange hero.
@Composable
fun BrandTile() {
	Box(
		Modifier
			.size(AppLayout.brandTile)
			// White in both modes: it is part of the hero, which does not change with the mode.
			.background(StockFlow.colors.onPrimary, RoundedCornerShape(DesignTokens.Rounded.lg.dp))
			.padding(DesignTokens.Spacing.xs.dp),
		contentAlignment = Alignment.Center,
	) {
		Image(painterResource(Res.drawable.stockflow_glyph_light), contentDescription = null, modifier = Modifier.fillMaxWidth())
	}
}

// A headline on the hero. The words between asterisks are lit in the warm orange, as the reference lights its words.
@Composable
fun HeroHeadline(text: String, modifier: Modifier = Modifier) {
	val colors = StockFlow.colors
	val parts = text.split("*")
	BasicText(
		text = buildAnnotatedString {
			parts.forEachIndexed { index, part -> if (index % 2 == 1) withStyle(SpanStyle(color = colors.primaryWarm)) { append(part) } else append(part) }
		},
		modifier = modifier,
		style = StockFlowType.headlineXl.copy(color = colors.onPrimary),
	)
}

// A round button on the hero, with an icon: back, sign out. `label` is what a screen reader says.
@Composable
fun HeroIconButton(icon: AppIcon, label: String, onClick: () -> Unit, tag: String) {
	val colors = StockFlow.colors
	Box(
		Modifier
			.size(AppLayout.pillHeight - DesignTokens.Spacing.sm.dp)
			.clip(CircleShape)
			.background(colors.onPrimary.copy(alpha = DECOR_ALPHA))
			.clickable(role = Role.Button, onClick = onClick)
			.semantics { contentDescription = label }
			.testTag(tag),
		contentAlignment = Alignment.Center,
	) {
		Icon(icon, colors.onPrimary)
	}
}

// The sheet's title and the line under it, centered, as the reference opens its forms.
@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
	Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp)) {
		AppText(title, StockFlowType.headlineLg, StockFlow.colors.onSurface)
		if (subtitle != null) AppText(subtitle, StockFlowType.bodyMd.copy(textAlign = TextAlign.Center), StockFlow.colors.onSurfaceMuted)
	}
}
