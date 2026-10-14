package com.axzydev.stock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.axzydev.stock.ui.theme.AppLayout

// The app's line icons, drawn on a 24-unit grid with a round 1.8-unit stroke. There is no icon font or Material
// dependency (mobile-app.md section 7), so each one is a few lines of geometry.
enum class AppIcon { USER, LOCK, EYE, EYE_OFF, SEARCH, BACK, SIGN_OUT, SCAN }

private const val GRID = 24f
private const val STROKE = 1.8f

// An icon in a color. It is decorative: the control that holds it says what it does.
@Composable
fun Icon(icon: AppIcon, color: Color, modifier: Modifier = Modifier, size: Dp = AppLayout.iconSize) {
	Canvas(modifier.size(size).clearAndSetSemantics {}) {
		scale(this.size.width / GRID, this.size.height / GRID, pivot = Offset.Zero) { draw(icon, color) }
	}
}

private fun DrawScope.draw(icon: AppIcon, color: Color) {
	val line = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
	fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = line)
	when (icon) {
		AppIcon.USER -> {
			drawCircle(color, radius = 4f, center = Offset(12f, 8f), style = line)
			path { moveTo(4f, 21f); arcTo(Rect(4f, 13f, 20f, 29f), 180f, 180f, false) }
		}
		AppIcon.LOCK -> {
			drawRoundRect(color, topLeft = Offset(5f, 11f), size = Size(14f, 10f), cornerRadius = CornerRadius(2f), style = line)
			path { moveTo(8f, 11f); lineTo(8f, 7f); arcTo(Rect(8f, 3f, 16f, 11f), 180f, 180f, false); lineTo(16f, 11f) }
		}
		AppIcon.EYE, AppIcon.EYE_OFF -> {
			path { moveTo(2f, 12f); cubicTo(5f, 6f, 19f, 6f, 22f, 12f); cubicTo(19f, 18f, 5f, 18f, 2f, 12f); close() }
			drawCircle(color, radius = 3f, center = Offset(12f, 12f), style = line)
			if (icon == AppIcon.EYE_OFF) path { moveTo(4f, 4f); lineTo(20f, 20f) }
		}
		AppIcon.SEARCH -> {
			drawCircle(color, radius = 7f, center = Offset(11f, 11f), style = line)
			path { moveTo(16f, 16f); lineTo(21f, 21f) }
		}
		AppIcon.BACK -> path { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
		AppIcon.SIGN_OUT -> path {
			moveTo(9f, 4f); lineTo(5f, 4f); lineTo(5f, 20f); lineTo(9f, 20f)
			moveTo(10f, 12f); lineTo(20f, 12f); moveTo(16f, 8f); lineTo(20f, 12f); lineTo(16f, 16f)
		}
		AppIcon.SCAN -> path {
			// The corners of a viewfinder, and the bars of a code inside.
			moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f); moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
			moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f); moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
			for (x in listOf(7f, 10f, 12f, 15f, 17f)) { moveTo(x, 8f); lineTo(x, 16f) }
		}
	}
}
