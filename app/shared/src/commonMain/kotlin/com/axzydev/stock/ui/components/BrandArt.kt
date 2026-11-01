package com.axzydev.stock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.axzydev.stock.ui.theme.StockFlow

// The illustration of the entry screen, the same scene as the web's: a shelf platform with cartons, a small crate and a
// scanned label. The shelf floats, each carton bobs at its own pace, the label drifts, the check pulses and the scan
// line runs down the label. It is decorative, so it says nothing to a screen reader.
@Composable
fun BrandIllustration(modifier: Modifier = Modifier.height(190.dp)) {
	val colors = StockFlow.colors
	val motion = rememberInfiniteTransition(label = "entry-art")
	val scene by motion.animateFloat(-8f, 0f, infiniteRepeatable(tween(4500), RepeatMode.Reverse), label = "scene")
	val bobA by motion.animateFloat(0f, -5f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "bob-a")
	val bobB by motion.animateFloat(0f, -5f, infiniteRepeatable(tween(3800, delayMillis = 300), RepeatMode.Reverse), label = "bob-b")
	val bobC by motion.animateFloat(0f, -5f, infiniteRepeatable(tween(2900, delayMillis = 500), RepeatMode.Reverse), label = "bob-c")
	val labelDrift by motion.animateFloat(-5f, 5f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "label")
	val labelTilt by motion.animateFloat(-1f, 1.2f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "tilt")
	val check by motion.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "check")
	val scan by motion.animateFloat(0f, 44f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "scan")

	Canvas(modifier.fillMaxWidth()) {
		val scale = minOf(size.width / 320f, size.height / 280f)
		translate((size.width - 320f * scale) / 2f, (size.height - 280f * scale) / 2f) {
			withTransform({ translate(0f, scene * scale) }) {
				drawBrandScene(
					scale = scale,
					surface = colors.surfaceElevated,
					soft = colors.surfaceSoft,
					outline = colors.outline,
					outlineStrong = colors.outlineStrong,
					warm = colors.primaryWarm,
					primary = colors.primary,
					strong = colors.primaryStrong,
					ink = colors.onBackground,
					onPrimary = colors.onPrimary,
					bobA = bobA,
					bobB = bobB,
					bobC = bobC,
					labelDrift = labelDrift,
					labelTilt = labelTilt,
					check = check,
					scan = scan,
				)
			}
		}
	}
}

// The scene, in its own 320 x 280 units, scaled to the canvas.
private fun DrawScope.drawBrandScene(
	scale: Float,
	surface: Color,
	soft: Color,
	outline: Color,
	outlineStrong: Color,
	warm: Color,
	primary: Color,
	strong: Color,
	ink: Color,
	onPrimary: Color,
	bobA: Float,
	bobB: Float,
	bobC: Float,
	labelDrift: Float,
	labelTilt: Float,
	check: Float,
	scan: Float,
) {
	fun point(x: Float, y: Float) = Offset(x * scale, y * scale)

	fun face(points: List<Pair<Float, Float>>, color: Color, stroke: Color? = null) {
		val path = Path().apply {
			points.forEachIndexed { index, (x, y) ->
				val o = point(x, y)
				if (index == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
			}
			close()
		}
		drawPath(path, color)
		if (stroke != null) drawPath(path, stroke, style = Stroke(width = 2f * scale))
	}

	fun ring(x: Float, y: Float, r: Float, color: Color) {
		drawCircle(color, radius = r * scale, center = point(x, y), style = Stroke(width = 2f * scale))
	}

	fun dot(x: Float, y: Float, r: Float, color: Color) {
		drawCircle(color, radius = r * scale, center = point(x, y))
	}

	// The three faces of an isometric carton: the base corner (cx, cy), the half width (s) and its height (h).
	fun carton(cx: Float, cy: Float, s: Float, h: Float, top: Color, left: Color, right: Color, stroke: Color? = null) {
		val b = cx to cy
		val l = cx - s to cy - s / 2f
		val r = cx + s to cy - s / 2f
		val bt = cx to cy - h
		val lt = cx - s to cy - h - s / 2f
		val rt = cx + s to cy - h - s / 2f
		val tp = cx to cy - h - s
		face(listOf(bt, rt, tp, lt), top, stroke)
		face(listOf(l, b, bt, lt), left, stroke)
		face(listOf(b, r, rt, bt), right, stroke)
	}

	// The quiet marks of the background.
	ring(36f, 42f, 22f, outline)
	ring(286f, 58f, 12f, outline)
	dot(18f, 168f, 4f, warm)
	dot(300f, 206f, 3.5f, outlineStrong)

	// The shelf platform, with a thin edge so it reads as a board.
	face(listOf(160f to 176f, 256f to 224f, 160f to 272f, 64f to 224f), soft, outline)
	face(listOf(160f to 168f, 256f to 216f, 160f to 264f, 64f to 216f), surface, outline)

	// The white stock carton, at the back.
	withTransform({ translate(0f, bobA * scale) }) {
		carton(206f, 182f, 29f, 52f, surface, soft, surface, outline)
	}
	// The small crate.
	withTransform({ translate(0f, bobB * scale) }) {
		carton(88f, 226f, 20f, 18f, soft, outline, outlineStrong, outline)
	}
	// The orange carton, at the front.
	withTransform({ translate(0f, bobC * scale) }) {
		carton(136f, 210f, 38f, 37f, warm, strong, primary)
	}

	// The scanned label: it drifts, and its scan line runs down the bars.
	withTransform({
		translate(0f, labelDrift * scale)
		rotate(-7f + labelTilt, point(268f, 168f))
	}) {
		drawRoundRect(surface, topLeft = point(214f, 140f), size = Size(74f * scale, 56f * scale), cornerRadius = CornerRadius(9f * scale))
		drawRoundRect(
			outline,
			topLeft = point(214f, 140f),
			size = Size(74f * scale, 56f * scale),
			cornerRadius = CornerRadius(9f * scale),
			style = Stroke(width = 2f * scale),
		)
		var barX = 222f
		for (width in listOf(3f, 2f, 5f, 2f, 4f, 2f, 4f)) {
			drawRect(ink, topLeft = point(barX, 150f), size = Size(width * scale, 22f * scale))
			barX += width + 3f
		}
		drawRoundRect(outline, topLeft = point(222f, 180f), size = Size(50f * scale, 4f * scale), cornerRadius = CornerRadius(2f * scale))
		drawRoundRect(outline, topLeft = point(222f, 187f), size = Size(32f * scale, 4f * scale), cornerRadius = CornerRadius(2f * scale))
		// The scan line.
		drawRoundRect(
			primary,
			topLeft = point(220f, 150f + scan),
			size = Size(64f * scale, 2.5f * scale),
			cornerRadius = CornerRadius(2f * scale),
		)
	}

	// The check of a completed count.
	withTransform({ scale(check, check, point(272f, 148f)) }) {
		dot(272f, 148f, 13f, primary)
		val a = point(266f, 148f)
		val b = point(270f, 152f)
		val c = point(278f, 143f)
		val path = Path().apply {
			moveTo(a.x, a.y)
			lineTo(b.x, b.y)
			lineTo(c.x, c.y)
		}
		drawPath(path, onPrimary, style = Stroke(width = 2.5f * scale))
	}
}
