package com.axzydev.stock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.axzydev.stock.brand.reducedMotionEnabled
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// The motion of the bento design, the same as the web's: numbers that climb, tiles that rise in order, a gauge that
// draws itself and water that moves. All of it stands still when the person asked for less motion.

private const val ARRIVAL_MS = 900
private const val RISE_MS = 420
private const val RISE_STEP_MS = 70L
private const val WAVE_MS = 3200

// The low tide of a wave: an empty queue is calm water, not a tile without its visual.
private const val WAVE_FLOOR = 0.2f

/** A number that climbs to [target] when it arrives, and again when it changes. */
@Composable
fun rememberCountUp(target: Int): Int {
	if (reducedMotionEnabled()) return target
	val shown = remember { Animatable(0f) }
	LaunchedEffect(target) { shown.animateTo(target.toFloat(), tween(ARRIVAL_MS, easing = FastOutSlowInEasing)) }
	return shown.value.roundToInt()
}

/** Fades in and rises into place, after the [order] pieces before it. */
@Composable
fun Modifier.riseIn(order: Int): Modifier {
	if (reducedMotionEnabled()) return this
	val progress = remember { Animatable(0f) }
	LaunchedEffect(Unit) {
		delay(order * RISE_STEP_MS)
		progress.animateTo(1f, tween(RISE_MS, easing = FastOutSlowInEasing))
	}
	val rise = with(LocalDensity.current) { 12.dp.toPx() }
	return graphicsLayer {
		alpha = progress.value
		translationY = (1f - progress.value) * rise
	}
}

/** A half-circle gauge: [fraction] of the arc drawn over its track, growing when it appears. */
@Composable
fun BentoGauge(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 92.dp, stroke: Dp = 13.dp) {
	val target = fraction.coerceIn(0f, 1f)
	val shown = remember { Animatable(if (reducedMotionEnabled()) target else 0f) }
	LaunchedEffect(target) { shown.animateTo(target, tween(ARRIVAL_MS, easing = FastOutSlowInEasing)) }
	Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
		val line = stroke.toPx()
		val radius = min((size.width - line) / 2f, size.height - line / 2f)
		val corner = Offset(size.width / 2f - radius, size.height - line / 2f - radius)
		val box = Size(radius * 2f, radius * 2f)
		drawArc(track, 180f, 180f, false, corner, box, style = Stroke(line, cap = StrokeCap.Round))
		if (shown.value > 0f) drawArc(color, 180f, 180f * shown.value, false, corner, box, style = Stroke(line, cap = StrokeCap.Round))
	}
}

/** Two bands of water moving at the foot of a tile. [level] is how full it is, from 0 to 1. */
@Composable
fun BentoWave(level: Float, color: Color, modifier: Modifier = Modifier) {
	val phase: State<Float> =
		if (reducedMotionEnabled()) {
			remember { mutableFloatStateOf(0f) }
		} else {
			rememberInfiniteTransition().animateFloat(
				initialValue = 0f,
				targetValue = (2 * PI).toFloat(),
				animationSpec = infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing), RepeatMode.Restart),
			)
		}
	val target = max(WAVE_FLOOR, level.coerceIn(0f, 1f))
	val shown = remember { Animatable(if (reducedMotionEnabled()) target else 0f) }
	LaunchedEffect(target) { shown.animateTo(target, tween(ARRIVAL_MS, easing = FastOutSlowInEasing)) }
	Canvas(modifier.clearAndSetSemantics {}) {
		val amplitude = size.height * 0.09f
		val surface = size.height * (1f - shown.value)
		fun band(shift: Float) = Path().apply {
			moveTo(0f, size.height)
			var x = 0f
			while (x <= size.width) {
				lineTo(x, surface + amplitude * sin(x / size.width * 3f * PI.toFloat() + phase.value + shift))
				x += 4f
			}
			lineTo(size.width, surface + amplitude * sin(3f * PI.toFloat() + phase.value + shift))
			lineTo(size.width, size.height)
			close()
		}
		drawPath(band(1.6f), color.copy(alpha = 0.18f))
		drawPath(band(0f), color.copy(alpha = 0.3f))
	}
}
