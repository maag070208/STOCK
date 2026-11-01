package com.axzydev.stock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.axzydev.stock.brand.reducedMotionEnabled
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.scanner.CameraAccess
import com.axzydev.stock.scanner.CameraPreview
import com.axzydev.stock.scanner.rememberCameraPermission
import com.axzydev.stock.ui.theme.AppBento
import com.axzydev.stock.ui.theme.BentoTone
import com.axzydev.stock.ui.theme.DesignTokens
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType
import kotlinx.coroutines.delay

// How the app reads a code, on every screen that asks for one (docs/modules/barcode-scanning.md): the camera comes first,
// ready to read; whoever cannot scan writes in a search box, and what they write finds results as they go. There is no
// field that needs the exact code.

private val CAMERA_HEIGHT = 260.dp
private const val SCAN_LINE_MS = 1800
private const val SEARCH_WAIT_MS = 300L
private const val MIN_SEARCH_LENGTH = 2

// The camera, with its frame and what to aim at, or what it needs to run. Each new code is felt in the hand.
@Composable
fun ScannerPanel(instruction: String, onScanned: (String) -> Unit) {
	val permission = rememberCameraPermission()
	val shape = RoundedCornerShape(AppBento.card)
	val haptics = LocalHapticFeedback.current
	var last by remember { mutableStateOf<String?>(null) }
	val latest by rememberUpdatedState(onScanned)
	if (permission.access == CameraAccess.GRANTED) {
		Box(Modifier.fillMaxWidth().height(CAMERA_HEIGHT).clip(shape).testTag("camera")) {
			CameraPreview(
				onScanned = { value ->
					if (value != last) {
						last = value
						haptics.performHapticFeedback(HapticFeedbackType.LongPress)
					}
					latest(value)
				},
				modifier = Modifier.fillMaxSize(),
			)
			ScanFrame()
			Box(Modifier.align(Alignment.BottomCenter).padding(DesignTokens.Spacing.md.dp).background(AppBento.ink().copy(alpha = 0.72f), AppBento.pill).padding(horizontal = DesignTokens.Spacing.md.dp, vertical = DesignTokens.Spacing.xs.dp)) {
				AppText(instruction, StockFlowType.labelMd, AppBento.onInk(), maxLines = 1)
			}
		}
		return
	}
	// Without the camera there is still a way in: the panel says what it needs, and the search box below keeps working.
	val colors = AppBento.tone(BentoTone.Ink)
	Column(
		Modifier
			.fillMaxWidth()
			.background(colors.container, shape)
			.padding(DesignTokens.Spacing.xl.dp)
			.testTag("camera-${permission.access.name.lowercase()}"),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.dp),
	) {
		Icon(AppIcon.SCAN, StockFlow.colors.primary, size = 44.dp)
		AppText(instruction, StockFlowType.titleMd, colors.content, maxLines = 2)
		val message = when (permission.access) {
			CameraAccess.ASK -> Text.CAMERA_ASK
			CameraAccess.DENIED -> Text.CAMERA_DENIED
			else -> Text.CAMERA_UNAVAILABLE
		}
		AppText(text(message), StockFlowType.bodyMd, colors.content.copy(alpha = 0.75f))
		if (permission.access != CameraAccess.UNAVAILABLE) PrimaryButton(text(Text.CAMERA_ALLOW), permission.request, tag = "camera-allow", icon = AppIcon.SCAN)
	}
}

// The four corners to aim with, and the line that runs across them while the camera reads.
@Composable
private fun ScanFrame() {
	val primary = StockFlow.colors.primary
	val travel by if (reducedMotionEnabled()) {
		remember { mutableStateOf(0.5f) }
	} else {
		rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(SCAN_LINE_MS, easing = LinearEasing), RepeatMode.Reverse))
	}
	Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
		val stroke = 4.dp.toPx()
		val left = size.width * 0.14f
		val right = size.width * 0.86f
		val top = size.height * 0.2f
		val bottom = size.height * 0.72f
		val arm = 26.dp.toPx()
		fun corner(x: Float, y: Float, dx: Float, dy: Float) {
			drawLine(primary, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
			drawLine(primary, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
		}
		corner(left, top, 1f, 1f)
		corner(right, top, -1f, 1f)
		corner(left, bottom, 1f, -1f)
		corner(right, bottom, -1f, -1f)
		val y = top + (bottom - top) * travel
		drawLine(primary.copy(alpha = 0.85f), Offset(left + 8.dp.toPx(), y), Offset(right - 8.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round)
	}
}

/**
 * Scan first, search second: the camera, then a search box, then [results], which the screen filters or fills as the
 * person writes. While they write the camera steps aside, so the results are in view above the keyboard. [onSearch] runs
 * a moment after the last key; the keyboard's Search key runs [onSubmit], the same as reading the code.
 */
@Composable
fun ScanSearch(
	instruction: String,
	searchLabel: String,
	query: String,
	onQueryChange: (String) -> Unit,
	onScanned: (String) -> Unit,
	onSubmit: () -> Unit,
	tag: String,
	error: String? = null,
	searching: Boolean = false,
	onSearch: (() -> Unit)? = null,
	results: @Composable () -> Unit = {},
) {
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	var writing by remember { mutableStateOf(false) }
	val isWriting by rememberUpdatedState(writing)
	val latestSearch by rememberUpdatedState(onSearch)
	LaunchedEffect(query) {
		if (latestSearch == null || query.trim().length < MIN_SEARCH_LENGTH) return@LaunchedEffect
		delay(SEARCH_WAIT_MS)
		if (isWriting) latestSearch?.invoke()
	}
	AnimatedVisibility(visible = !writing) {
		ScannerPanel(instruction, onScanned)
	}
	TextInput(
		label = searchLabel,
		value = query,
		onValueChange = onQueryChange,
		tag = tag,
		icon = AppIcon.SEARCH,
		error = error,
		imeAction = ImeAction.Search,
		onDone = {
			focus.clearFocus()
			keyboard?.hide()
			onSubmit()
		},
		onFocusChanged = { writing = it },
	)
	if (searching) AppText(text(Text.LOOKUP_SEARCHING), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
	results()
}

/** The search box of a list with nothing to scan: it filters as the person writes. */
@Composable
fun ListSearch(label: String, query: String, onQueryChange: (String) -> Unit, tag: String) {
	val focus = LocalFocusManager.current
	val keyboard = LocalSoftwareKeyboardController.current
	TextInput(
		label = label,
		value = query,
		onValueChange = onQueryChange,
		tag = tag,
		icon = AppIcon.SEARCH,
		imeAction = ImeAction.Search,
		onDone = {
			focus.clearFocus()
			keyboard?.hide()
		},
	)
}

/** Whether a row answers what was written, in any of its texts and ignoring capitals. */
fun String.matches(vararg texts: String?): Boolean {
	val wanted = trim()
	return wanted.isEmpty() || texts.any { it?.contains(wanted, ignoreCase = true) == true }
}

/** A state with nothing to work on: what happened, in a friendly way, and what comes next. */
@Composable
fun BentoState(title: String, tag: String, hint: String? = null, icon: AppIcon = AppIcon.CHECK, tone: BentoTone = BentoTone.Orange) {
	val colors = AppBento.tone(tone)
	Column(
		Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.xl.dp).testTag(tag),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.dp),
	) {
		Box(Modifier.size(72.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
			Icon(icon, colors.content, size = 32.dp)
		}
		AppText(title, StockFlowType.titleMd, StockFlow.colors.onSurface, maxLines = 2)
		if (hint != null) AppText(hint, StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted, maxLines = 3)
	}
}
