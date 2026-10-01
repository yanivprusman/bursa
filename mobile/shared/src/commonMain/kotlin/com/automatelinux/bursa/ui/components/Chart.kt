package com.automatelinux.bursa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val END_INSET = 8.dp

/** A bare line: the shape of a series, no axes. Time runs left to right in any language. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 2.dp.toPx()
        val h = size.height - pad * 2
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = pad + h * (1 - ((v - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The price chart on a detail page. Touch and slide to read any point: [onScrub] gets the
 * index under the finger, and null when it lifts. A sideways slide belongs to the chart; an
 * up-down one is handed back so the page still scrolls.
 */
@Composable
fun PriceChart(
    values: List<Double>,
    /** Previous close, drawn as a dashed line; null to leave it out. */
    baseline: Double?,
    color: Color,
    guide: Color,
    modifier: Modifier = Modifier,
    onScrub: (Int?) -> Unit,
) {
    var scrub by remember(values) { mutableStateOf<Int?>(null) }
    val report by rememberUpdatedState(onScrub)

    Canvas(
        modifier.pointerInput(values) {
            if (values.size < 2) return@pointerInput
            fun indexAt(x: Float) = ((x / (size.width - END_INSET.toPx())) * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scrub = indexAt(down.position.x)
                report(scrub)
                var mine = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = abs(change.position.x - down.position.x)
                    val dy = abs(change.position.y - down.position.y)
                    if (!mine && dx > viewConfiguration.touchSlop && dx > dy) mine = true
                    if (!mine && dy > viewConfiguration.touchSlop) break
                    if (mine) change.consume()
                    scrub = indexAt(change.position.x)
                    report(scrub)
                }
                scrub = null
                report(null)
            }
        },
    ) {
        if (values.size < 2) return@Canvas
        val lo = minOf(values.min(), baseline ?: values.min())
        val hi = maxOf(values.max(), baseline ?: values.max())
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val top = 10.dp.toPx()
        val bottom = 10.dp.toPx()
        val h = size.height - top - bottom
        // Leave room for the dot on the newest point.
        val w = size.width - END_INSET.toPx()
        fun x(i: Int) = w * i / (values.size - 1)
        fun y(v: Double) = top + h * (1 - ((v - lo) / span).toFloat())

        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f)), startY = top, endY = size.height))

        if (baseline != null) {
            drawLine(
                guide,
                Offset(0f, y(baseline)),
                Offset(size.width, y(baseline)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )
        }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val at = scrub
        if (at != null) {
            val p = Offset(x(at), y(values[at]))
            drawLine(guide, Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), p)
            drawCircle(color, 4.5.dp.toPx(), p)
        } else {
            // A dot on the newest point: this is where the price is now.
            val p = Offset(x(values.lastIndex), y(values.last()))
            drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), p)
            drawCircle(color, 3.5.dp.toPx(), p)
        }
    }
}
